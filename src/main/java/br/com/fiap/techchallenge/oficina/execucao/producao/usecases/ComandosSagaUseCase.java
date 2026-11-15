package br.com.fiap.techchallenge.oficina.execucao.producao.usecases;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Reserva;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.StatusReserva;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.ItemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.StatusExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.OrdemExecucaoGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.PublicadorEventosGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Os três comandos que o orquestrador manda à Execução ({@code contratos-mensagens.md} §3).
 *
 * <p><b>Idempotência pela chave de negócio</b> (ADR 010): um comando repetido — reenvio
 * por timeout, entrega duplicada do broker — não repete o efeito, mas <b>responde de
 * novo</b> com base no estado atual. É o que destrava a saga quando a resposta anterior
 * se perdeu. Por isso não há inbox aqui: a duplicata precisa ser respondida.
 *
 * <p>Erro de negócio vira evento de resposta ({@code ReparoNaoLiberado}), nunca exceção.
 */
public class ComandosSagaUseCase {

    private static final java.util.Set<StatusExecucao> JA_LIBERADA =
            EnumSet.of(StatusExecucao.NA_FILA_REPARO, StatusExecucao.EM_REPARO, StatusExecucao.FINALIZADA);

    private final OrdemExecucaoGateway ordens;
    private final PecaGateway pecas;
    private final PublicadorEventosGateway eventos;
    private final Clock relogio;

    public ComandosSagaUseCase(OrdemExecucaoGateway ordens, PecaGateway pecas, PublicadorEventosGateway eventos,
                               Clock relogio) {
        this.ordens = ordens;
        this.pecas = pecas;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    public record AbrirOrdemExecucao(UUID messageId, OrdemServicoId osId, OrdemExecucao.Veiculo veiculo,
                                     String relatoCliente) {}

    public record LiberarParaReparo(UUID messageId, OrdemServicoId osId, UUID orcamentoId) {}

    public record CancelarOrdemExecucao(UUID messageId, OrdemServicoId osId, String motivo) {}

    // ---------------------------------------------------------------------

    public EventoExecucao abrir(AbrirOrdemExecucao cmd) {
        OrdemExecucao ordem = ordens.buscarComLock(cmd.osId())
                .orElseGet(() -> ordens.salvar(OrdemExecucao.abrir(cmd.osId(), cmd.veiculo(), cmd.relatoCliente(),
                        OffsetDateTime.now(relogio))));
        int posicao = posicaoNaFila(ordem);
        return responder(EventoExecucao.ordemExecucaoAberta(ordem, posicao), cmd.osId(), cmd.messageId());
    }

    /**
     * Pagamento confirmado: baixa as peças reservadas e põe a OS na fila de reparo.
     * Reserva expirada não é erro por si — se ainda houver saldo, reserva e baixa de novo;
     * se não houver, responde {@code ReparoNaoLiberado} e a saga estorna o pagamento.
     * Tudo ou nada: verifica todas as peças antes de baixar qualquer uma.
     */
    public EventoExecucao liberarParaReparo(LiberarParaReparo cmd) {
        Optional<OrdemExecucao> encontrada = ordens.buscarComLock(cmd.osId());
        if (encontrada.isEmpty()) {
            return responder(naoLiberado(cmd.osId(), "ORDEM_INEXISTENTE", List.of()), cmd.osId(), cmd.messageId());
        }
        OrdemExecucao ordem = encontrada.get();
        if (JA_LIBERADA.contains(ordem.status())) {
            return responder(EventoExecucao.reparoLiberado(ordem), cmd.osId(), cmd.messageId());
        }
        if (ordem.status() != StatusExecucao.AGUARDANDO_LIBERACAO) {
            String motivo = ordem.status() == StatusExecucao.CANCELADA ? "ORDEM_CANCELADA" : "DIAGNOSTICO_NAO_CONCLUIDO";
            return responder(EventoExecucao.reparoNaoLiberado(ordem, motivo, List.of()), cmd.osId(), cmd.messageId());
        }

        OffsetDateTime agora = OffsetDateTime.now(relogio);
        Map<PecaId, Peca> travadas = travarPecas(ordem);
        List<Map<String, Object>> faltantes = pecasFaltantes(ordem, travadas, agora);
        if (!faltantes.isEmpty()) {
            return responder(EventoExecucao.reparoNaoLiberado(ordem, "ESTOQUE_INSUFICIENTE", faltantes),
                    cmd.osId(), cmd.messageId());
        }

        for (ItemExecucao item : ordem.itensDePeca()) {
            Peca peca = travadas.get(PecaId.de(item.referenciaId()));
            Reserva reserva = reservaValida(peca, item, agora);
            if (reserva == null) {
                reserva = peca.reservar(ordem.osId(), item.quantidade(), agora, null);
                ordem.registrarReserva(item.id(), reserva.id());
            }
            peca.baixar(reserva.id(), agora);
        }
        travadas.values().forEach(pecas::salvar);
        ordem.liberarParaReparo(cmd.orcamentoId(), agora);
        ordens.salvar(ordem);
        return responder(EventoExecucao.reparoLiberado(ordem), cmd.osId(), cmd.messageId());
    }

    /** Compensação: libera as reservas ativas e cancela a ordem. Confirma sempre. */
    public EventoExecucao cancelar(CancelarOrdemExecucao cmd) {
        Optional<OrdemExecucao> encontrada = ordens.buscarComLock(cmd.osId());
        if (encontrada.isEmpty()) {
            return responder(EventoExecucao.ordemExecucaoCancelada(cmd.osId().valor().toString(), 0),
                    cmd.osId(), cmd.messageId());
        }
        OrdemExecucao ordem = encontrada.get();
        int liberadas = 0;
        if (ordem.status() != StatusExecucao.CANCELADA && ordem.status() != StatusExecucao.FINALIZADA) {
            for (Map.Entry<PecaId, Peca> e : travarPecas(ordem).entrySet()) {
                Peca peca = e.getValue();
                for (ItemExecucao item : ordem.itensDePeca()) {
                    if (item.reservaId() != null && PecaId.de(item.referenciaId()).equals(e.getKey())
                            && peca.reserva(item.reservaId()).status() == StatusReserva.ATIVA) {
                        peca.cancelarReserva(item.reservaId());
                        liberadas++;
                    }
                }
                pecas.salvar(peca);
            }
            ordem.cancelar(cmd.motivo(), OffsetDateTime.now(relogio));
            ordens.salvar(ordem);
        }
        // FINALIZADA não volta atrás, mas a saga precisa da confirmação para não travar.
        return responder(EventoExecucao.ordemExecucaoCancelada(ordem.osId().valor().toString(), liberadas),
                cmd.osId(), cmd.messageId());
    }

    // ---------------------------------------------------------------------

    private EventoExecucao responder(EventoExecucao evento, OrdemServicoId osId, UUID causa) {
        eventos.publicar(evento, osId, causa);
        return evento;
    }

    private EventoExecucao naoLiberado(OrdemServicoId osId, String motivo, List<Map<String, Object>> faltantes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("osId", osId.valor().toString());
        payload.put("motivo", motivo);
        payload.put("pecasFaltantes", faltantes);
        return new EventoExecucao("ReparoNaoLiberado", payload);
    }

    private int posicaoNaFila(OrdemExecucao ordem) {
        if (ordem.status() != StatusExecucao.NA_FILA_DIAGNOSTICO) {
            return 0;
        }
        List<OrdemExecucao> fila = ordens.listarPorStatus(EnumSet.of(StatusExecucao.NA_FILA_DIAGNOSTICO));
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).osId().equals(ordem.osId())) {
                return i + 1;
            }
        }
        return fila.size() + 1;
    }

    /** Trava cada peça uma vez só, em ordem de id — duas liberações simultâneas não se travam em ciclo. */
    private Map<PecaId, Peca> travarPecas(OrdemExecucao ordem) {
        Map<PecaId, Peca> travadas = new LinkedHashMap<>();
        ordem.itensDePeca().stream()
                .map(i -> PecaId.de(i.referenciaId()))
                .distinct()
                .sorted((a, b) -> a.valor().compareTo(b.valor()))
                .forEach(id -> travadas.put(id, pecas.buscarPorIdComLock(id)
                        .orElseThrow(() -> new PecaNaoEncontradaException(id))));
        return travadas;
    }

    private static Reserva reservaValida(Peca peca, ItemExecucao item, OffsetDateTime agora) {
        if (item.reservaId() == null) {
            return null;
        }
        Reserva r = peca.reserva(item.reservaId());
        return r.ativaEm(agora) ? r : null;
    }

    /** Para cada peça, o que falta reservar de novo (reserva vencida) precisa caber no saldo livre. */
    private static List<Map<String, Object>> pecasFaltantes(OrdemExecucao ordem, Map<PecaId, Peca> travadas,
                                                            OffsetDateTime agora) {
        Map<PecaId, Integer> necessario = new LinkedHashMap<>();
        for (ItemExecucao item : ordem.itensDePeca()) {
            PecaId id = PecaId.de(item.referenciaId());
            if (reservaValida(travadas.get(id), item, agora) == null) {
                necessario.merge(id, item.quantidade(), Integer::sum);
            }
        }
        List<Map<String, Object>> faltantes = new ArrayList<>();
        necessario.forEach((id, qtd) -> {
            int disponivel = travadas.get(id).saldoDisponivel(agora);
            if (qtd > disponivel) {
                faltantes.add(EventoExecucao.mapa("pecaId", id.valor().toString(), "quantidade", qtd,
                        "disponivel", disponivel));
            }
        });
        return faltantes;
    }
}
