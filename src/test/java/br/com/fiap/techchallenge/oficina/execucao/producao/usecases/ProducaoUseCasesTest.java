package br.com.fiap.techchallenge.oficina.execucao.producao.usecases;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.EstoqueInsuficienteException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.StatusReserva;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.ItemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucaoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.StatusExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.TransicaoExecucaoInvalidaException;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.execucao.suporte.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Comandos da saga e trabalho do mecânico contra gateways em memória. */
class ProducaoUseCasesTest {

    private static final Duration VALIDADE = Duration.ofHours(48);
    private static final OrdemExecucao.Veiculo ARGO = new OrdemExecucao.Veiculo("ABC1D23", "Fiat", "Argo", 2022);

    private final Fakes.Ordens ordens = new Fakes.Ordens();
    private final Fakes.Pecas pecas = new Fakes.Pecas();
    private final Fakes.Servicos servicos = new Fakes.Servicos();
    private final Fakes.Eventos eventos = new Fakes.Eventos();
    private final Fakes.Relogio relogio = new Fakes.Relogio();

    private ComandosSagaUseCase comandos;
    private TrabalhoMecanicoUseCase mecanico;
    private Peca pastilha;
    private Servico alinhamento;

    @BeforeEach
    void preparar() {
        comandos = new ComandosSagaUseCase(ordens, pecas, eventos, relogio);
        mecanico = new TrabalhoMecanicoUseCase(ordens, servicos, pecas, eventos, VALIDADE, relogio);
        pastilha = pecas.com(Peca.novo("Pastilha de freio", Dinheiro.de("95.00"), 4));
        alinhamento = servicos.com(Servico.novo("Alinhamento", Dinheiro.de("120.00")));
    }

    private OrdemServicoId abrir() {
        OrdemServicoId os = OrdemServicoId.novo();
        comandos.abrir(new ComandosSagaUseCase.AbrirOrdemExecucao(UUID.randomUUID(), os, ARGO, "Barulho"));
        return os;
    }

    private OrdemServicoId ateAguardandoLiberacao(int pastilhas) {
        OrdemServicoId os = abrir();
        mecanico.iniciarDiagnostico(os, "joao");
        mecanico.adicionarServico(os, alinhamento.id(), null);
        mecanico.adicionarPeca(os, pastilha.id(), pastilhas);
        mecanico.concluirDiagnostico(os);
        return os;
    }

    private EventoExecucao liberar(OrdemServicoId os) {
        return comandos.liberarParaReparo(new ComandosSagaUseCase.LiberarParaReparo(UUID.randomUUID(), os,
                UUID.randomUUID()));
    }

    // ---------- AbrirOrdemExecucao ----------

    @Test
    void abrirColocaNaFilaEInformaAPosicao() {
        abrir();
        OrdemServicoId segunda = abrir();

        EventoExecucao resposta = eventos.ultimo();
        assertEquals("OrdemExecucaoAberta", resposta.tipo());
        assertEquals(2, resposta.payload().get("posicaoFila"));
        assertEquals(StatusExecucao.NA_FILA_DIAGNOSTICO, ordens.dados.get(segunda).status());
        assertEquals(2, mecanico.fila(StatusExecucao.Etapa.DIAGNOSTICO).size());
    }

    @Test
    @DisplayName("comando repetido não abre outra ordem, mas responde de novo (destrava a saga)")
    void abrirDuasVezesRespondeDeNovo() {
        OrdemServicoId os = abrir();
        UUID reenvio = UUID.randomUUID();

        comandos.abrir(new ComandosSagaUseCase.AbrirOrdemExecucao(reenvio, os, ARGO, "Barulho"));

        assertEquals(1, ordens.dados.size());
        assertEquals(List.of("OrdemExecucaoAberta", "OrdemExecucaoAberta"), eventos.tipos());
        assertEquals(reenvio, eventos.publicados.get(1).causationId());
    }

    // ---------- diagnóstico ----------

    @Test
    @DisplayName("concluir o diagnóstico reserva as peças com validade e publica os itens")
    void concluirDiagnosticoReservaEPublica() {
        OrdemServicoId os = ateAguardandoLiberacao(2);

        OrdemExecucao ordem = ordens.dados.get(os);
        assertEquals(StatusExecucao.AGUARDANDO_LIBERACAO, ordem.status());
        assertEquals(2, pastilha.saldoDisponivel(Fakes.AGORA));
        ItemExecucao item = ordem.itensDePeca().get(0);
        assertNotNull(item.reservaId());
        assertEquals(Fakes.AGORA.plus(VALIDADE), pastilha.reserva(item.reservaId()).validaAte());

        EventoExecucao concluido = eventos.ultimo();
        assertEquals("DiagnosticoConcluido", concluido.tipo());
        assertEquals("execucao.diagnostico-concluido", concluido.routingKey());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) concluido.payload().get("itens");
        assertEquals(2, itens.size());
        assertEquals("120.00", itens.get(0).get("valorUnitario"));
        assertEquals(List.of("OrdemExecucaoAberta", "DiagnosticoIniciado", "DiagnosticoConcluido"), eventos.tipos());
    }

    @Test
    void semEstoqueNaoConcluiNemReserva() {
        OrdemServicoId os = abrir();
        mecanico.iniciarDiagnostico(os, "joao");
        mecanico.adicionarPeca(os, pastilha.id(), 4);
        pecas.com(Peca.reconstituir(pastilha.id(), "Pastilha de freio", Dinheiro.de("95.00"), 1, List.of()));

        assertThrows(EstoqueInsuficienteException.class, () -> mecanico.concluirDiagnostico(os));
        assertThrows(EstoqueInsuficienteException.class, () -> mecanico.adicionarPeca(os, pastilha.id(), 5));
    }

    @Test
    void itensDoDiagnostico() {
        OrdemServicoId os = abrir();
        mecanico.iniciarDiagnostico(os, "joao");
        OrdemExecucao ordem = mecanico.adicionarServico(os, alinhamento.id(), new BigDecimal("150.00"));
        assertEquals(Dinheiro.de("150.00"), ordem.itens().get(0).valorUnitario(), "valor negociado prevalece");

        mecanico.removerItem(os, ordem.itens().get(0).id());
        assertTrue(mecanico.buscar(os).itens().isEmpty());
        assertThrows(ServicoNaoEncontradoException.class,
                () -> mecanico.adicionarServico(os, ServicoId.novo(), null));
        assertThrows(PecaNaoEncontradaException.class, () -> mecanico.adicionarPeca(os, PecaId.novo(), 1));
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> mecanico.concluirDiagnostico(os));
        assertThrows(OrdemExecucaoNaoEncontradaException.class, () -> mecanico.buscar(OrdemServicoId.novo()));
    }

    // ---------- LiberarParaReparo ----------

    @Test
    @DisplayName("pagamento confirmado: baixa as peças reservadas e põe na fila de reparo")
    void liberarBaixaAsReservas() {
        OrdemServicoId os = ateAguardandoLiberacao(2);

        EventoExecucao resposta = liberar(os);

        assertEquals("ReparoLiberado", resposta.tipo());
        assertEquals(2, pastilha.quantidadeTotal());
        assertEquals(StatusExecucao.NA_FILA_REPARO, ordens.dados.get(os).status());
        assertEquals(1, mecanico.fila(StatusExecucao.Etapa.REPARO).size());
        assertEquals("ReparoLiberado", liberar(os).tipo(), "repetido: responde de novo sem baixar outra vez");
        assertEquals(2, pastilha.quantidadeTotal());
    }

    @Test
    @DisplayName("reserva vencida mas ainda há saldo: reserva de novo e libera")
    void reservaVencidaComSaldoAindaLibera() {
        OrdemServicoId os = ateAguardandoLiberacao(2);
        relogio.avancar(VALIDADE.plusMinutes(1));

        assertEquals("ReparoLiberado", liberar(os).tipo());
        assertEquals(2, pastilha.quantidadeTotal());
    }

    @Test
    @DisplayName("reserva vencida e a peça foi para outra OS: ReparoNaoLiberado — a saga estorna")
    void reservaVencidaSemSaldoNaoLibera() {
        OrdemServicoId os = ateAguardandoLiberacao(3);
        relogio.avancar(VALIDADE.plusMinutes(1));
        ateAguardandoLiberacao(4);            // outra OS reserva todo o estoque liberado

        EventoExecucao resposta = liberar(os);

        assertEquals("ReparoNaoLiberado", resposta.tipo());
        assertEquals("ESTOQUE_INSUFICIENTE", resposta.payload().get("motivo"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> faltantes = (List<Map<String, Object>>) resposta.payload().get("pecasFaltantes");
        assertEquals(3, faltantes.get(0).get("quantidade"));
        assertEquals(StatusExecucao.AGUARDANDO_LIBERACAO, ordens.dados.get(os).status(), "nada mudou");
        assertEquals(4, pastilha.quantidadeTotal(), "nenhuma baixa parcial");
    }

    @Test
    void liberarOrdemInexistenteCanceladaOuSemDiagnostico() {
        assertEquals("ORDEM_INEXISTENTE", liberar(OrdemServicoId.novo()).payload().get("motivo"));

        OrdemServicoId naFila = abrir();
        assertEquals("DIAGNOSTICO_NAO_CONCLUIDO", liberar(naFila).payload().get("motivo"));

        comandos.cancelar(new ComandosSagaUseCase.CancelarOrdemExecucao(UUID.randomUUID(), naFila, "ORCAMENTO_RECUSADO"));
        assertEquals("ORDEM_CANCELADA", liberar(naFila).payload().get("motivo"));
    }

    // ---------- CancelarOrdemExecucao ----------

    @Test
    @DisplayName("compensação: libera as reservas, cancela e confirma — também se repetida")
    void cancelarLiberaReservas() {
        OrdemServicoId os = ateAguardandoLiberacao(2);

        EventoExecucao resposta = comandos.cancelar(
                new ComandosSagaUseCase.CancelarOrdemExecucao(UUID.randomUUID(), os, "PAGAMENTO_RECUSADO"));

        assertEquals("OrdemExecucaoCancelada", resposta.tipo());
        assertEquals(1, resposta.payload().get("reservasLiberadas"));
        assertEquals(4, pastilha.saldoDisponivel(Fakes.AGORA));
        ItemExecucao item = ordens.dados.get(os).itensDePeca().get(0);
        assertEquals(StatusReserva.CANCELADA, pastilha.reserva(item.reservaId()).status());
        assertEquals(StatusExecucao.CANCELADA, ordens.dados.get(os).status());
        assertEquals("PAGAMENTO_RECUSADO", ordens.dados.get(os).motivoCancelamento());

        EventoExecucao repetida = comandos.cancelar(
                new ComandosSagaUseCase.CancelarOrdemExecucao(UUID.randomUUID(), os, "PAGAMENTO_RECUSADO"));
        assertEquals(0, repetida.payload().get("reservasLiberadas"));
    }

    @Test
    void cancelarOrdemQueNuncaExistiuTambemConfirma() {
        OrdemServicoId os = OrdemServicoId.novo();

        EventoExecucao resposta = comandos.cancelar(
                new ComandosSagaUseCase.CancelarOrdemExecucao(UUID.randomUUID(), os, "ORCAMENTO_NAO_GERADO"));

        assertEquals("OrdemExecucaoCancelada", resposta.tipo());
        assertEquals(os.valor().toString(), resposta.payload().get("osId"));
    }

    // ---------- reparo ----------

    @Test
    void reparoCompletoComunicaFinalizacao() {
        OrdemServicoId os = ateAguardandoLiberacao(1);
        liberar(os);

        mecanico.iniciarReparo(os, "maria");
        OrdemExecucao finalizada = mecanico.finalizarReparo(os);

        assertEquals(StatusExecucao.FINALIZADA, finalizada.status());
        assertEquals("maria", finalizada.mecanicoReparo());
        assertEquals(List.of("ReparoIniciado", "ReparoFinalizado"),
                eventos.tipos().subList(eventos.tipos().size() - 2, eventos.tipos().size()));
        assertEquals("OrdemExecucaoCancelada", comandos.cancelar(new ComandosSagaUseCase.CancelarOrdemExecucao(
                UUID.randomUUID(), os, "X")).tipo(), "finalizada não volta atrás, mas a saga é confirmada");
        assertEquals(StatusExecucao.FINALIZADA, ordens.dados.get(os).status());
    }
}
