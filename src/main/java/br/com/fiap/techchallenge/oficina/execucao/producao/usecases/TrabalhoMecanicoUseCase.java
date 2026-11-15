package br.com.fiap.techchallenge.oficina.execucao.producao.usecases;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.EstoqueInsuficienteException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Reserva;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.ItemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucaoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.StatusExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.OrdemExecucaoGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.PublicadorEventosGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que o mecânico faz na oficina: pega a OS da fila, diagnostica, registra itens,
 * conclui (reservando peças) e executa o reparo. Cada passo que interessa à saga vira
 * evento no outbox, na mesma transação.
 */
public class TrabalhoMecanicoUseCase {

    private final OrdemExecucaoGateway ordens;
    private final ServicoGateway servicos;
    private final PecaGateway pecas;
    private final PublicadorEventosGateway eventos;
    private final Duration validadeReserva;
    private final Clock relogio;

    public TrabalhoMecanicoUseCase(OrdemExecucaoGateway ordens, ServicoGateway servicos, PecaGateway pecas,
                                   PublicadorEventosGateway eventos, Duration validadeReserva, Clock relogio) {
        this.ordens = ordens;
        this.servicos = servicos;
        this.pecas = pecas;
        this.eventos = eventos;
        this.validadeReserva = validadeReserva;
        this.relogio = relogio;
    }

    public List<OrdemExecucao> fila(StatusExecucao.Etapa etapa) {
        EnumSet<StatusExecucao> status = EnumSet.noneOf(StatusExecucao.class);
        for (StatusExecucao s : StatusExecucao.values()) {
            if (s.etapa() == etapa) {
                status.add(s);
            }
        }
        return ordens.listarPorStatus(status);
    }

    public OrdemExecucao buscar(OrdemServicoId osId) {
        return ordens.buscar(osId).orElseThrow(() -> new OrdemExecucaoNaoEncontradaException(osId));
    }

    public OrdemExecucao iniciarDiagnostico(OrdemServicoId osId, String mecanico) {
        OrdemExecucao ordem = travar(osId);
        ordem.iniciarDiagnostico(mecanico, agora());
        return salvarEPublicar(ordem, EventoExecucao.diagnosticoIniciado(ordem));
    }

    /** @param valor preço cobrado; nulo usa o valor base do catálogo */
    public OrdemExecucao adicionarServico(OrdemServicoId osId, ServicoId servicoId, BigDecimal valor) {
        OrdemExecucao ordem = travar(osId);
        Servico servico = servicos.buscarPorId(servicoId).orElseThrow(() -> new ServicoNaoEncontradoException(servicoId));
        ordem.adicionarServico(servico.id().valor(), servico.descricao(),
                valor == null ? servico.valorBase() : Dinheiro.de(valor));
        return ordens.salvar(ordem);
    }

    /** Confere o saldo na hora (sem reservar): o mecânico já sabe se a peça existe. */
    public OrdemExecucao adicionarPeca(OrdemServicoId osId, PecaId pecaId, int quantidade) {
        OrdemExecucao ordem = travar(osId);
        Peca peca = pecas.buscarPorId(pecaId).orElseThrow(() -> new PecaNaoEncontradaException(pecaId));
        int disponivel = peca.saldoDisponivel(agora());
        if (quantidade > disponivel) {
            throw new EstoqueInsuficienteException(pecaId, quantidade, disponivel);
        }
        ordem.adicionarPeca(peca.id().valor(), peca.descricao(), quantidade, peca.valorUnitario());
        return ordens.salvar(ordem);
    }

    public OrdemExecucao removerItem(OrdemServicoId osId, UUID itemId) {
        OrdemExecucao ordem = travar(osId);
        ordem.removerItem(itemId);
        return ordens.salvar(ordem);
    }

    /**
     * Conclui o diagnóstico reservando as peças, com validade. Falta de estoque aqui não é
     * falha de saga: a conclusão é recusada (422) e nada fica reservado (rollback).
     */
    public OrdemExecucao concluirDiagnostico(OrdemServicoId osId) {
        OrdemExecucao ordem = travar(osId);
        OffsetDateTime agora = agora();
        OffsetDateTime validaAte = agora.plus(validadeReserva);
        List<Map<String, Object>> reservas = new ArrayList<>();
        for (ItemExecucao item : ordem.itensDePeca()) {
            PecaId pecaId = PecaId.de(item.referenciaId());
            Peca peca = pecas.buscarPorIdComLock(pecaId).orElseThrow(() -> new PecaNaoEncontradaException(pecaId));
            Reserva reserva = peca.reservar(ordem.osId(), item.quantidade(), agora, validaAte);
            pecas.salvar(peca);
            ordem.registrarReserva(item.id(), reserva.id());
            reservas.add(EventoExecucao.mapa("pecaId", pecaId.valor().toString(),
                    "quantidade", item.quantidade(), "validaAte", validaAte.toString()));
        }
        ordem.concluirDiagnostico(agora);
        return salvarEPublicar(ordem, EventoExecucao.diagnosticoConcluido(ordem, reservas));
    }

    public OrdemExecucao iniciarReparo(OrdemServicoId osId, String mecanico) {
        OrdemExecucao ordem = travar(osId);
        ordem.iniciarReparo(mecanico, agora());
        return salvarEPublicar(ordem, EventoExecucao.reparoIniciado(ordem));
    }

    /** Comunica a finalização ao OS Service (enunciado). */
    public OrdemExecucao finalizarReparo(OrdemServicoId osId) {
        OrdemExecucao ordem = travar(osId);
        ordem.finalizarReparo(agora());
        return salvarEPublicar(ordem, EventoExecucao.reparoFinalizado(ordem));
    }

    private OrdemExecucao travar(OrdemServicoId osId) {
        return ordens.buscarComLock(osId).orElseThrow(() -> new OrdemExecucaoNaoEncontradaException(osId));
    }

    private OrdemExecucao salvarEPublicar(OrdemExecucao ordem, EventoExecucao evento) {
        OrdemExecucao salva = ordens.salvar(ordem);
        eventos.publicar(evento, ordem.osId(), null);
        return salva;
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }
}
