package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.ReservaId;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.execucao.suporte.Fakes.AGORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdemExecucaoTest {

    private OrdemExecucao ordem;

    @BeforeEach
    void abrir() {
        ordem = OrdemExecucao.abrir(OrdemServicoId.novo(),
                new OrdemExecucao.Veiculo("ABC1D23", "Fiat", "Argo", 2022), "Barulho", AGORA);
    }

    private ItemExecucao comDiagnosticoEmAndamento() {
        ordem.iniciarDiagnostico("joao", AGORA.plusMinutes(5));
        ordem.adicionarServico(UUID.randomUUID(), "Alinhamento", Dinheiro.de("120.00"));
        return ordem.adicionarPeca(UUID.randomUUID(), "Pastilha", 2, Dinheiro.de("95.00"));
    }

    @Test
    void cicloCompleto() {
        assertEquals(StatusExecucao.NA_FILA_DIAGNOSTICO, ordem.status());
        assertEquals(StatusExecucao.Etapa.DIAGNOSTICO, ordem.status().etapa());

        ItemExecucao peca = comDiagnosticoEmAndamento();
        ordem.registrarReserva(peca.id(), ReservaId.novo());
        ordem.concluirDiagnostico(AGORA.plusMinutes(40));
        assertEquals(StatusExecucao.AGUARDANDO_LIBERACAO, ordem.status());
        assertNull(ordem.status().etapa(), "aguardando o cliente: fora das filas");
        assertEquals(Dinheiro.de("310.00"), ordem.total());

        UUID orcamento = UUID.randomUUID();
        ordem.liberarParaReparo(orcamento, AGORA.plusHours(2));
        ordem.iniciarReparo("maria", AGORA.plusHours(3));
        ordem.finalizarReparo(AGORA.plusHours(5));

        assertEquals(StatusExecucao.FINALIZADA, ordem.status());
        assertEquals(orcamento, ordem.orcamentoId());
        assertEquals("joao", ordem.mecanicoDiagnostico());
        assertEquals("maria", ordem.mecanicoReparo());
        assertEquals(AGORA.plusHours(5), ordem.finalizadaEm());
    }

    @Test
    void etapasForaDeOrdemSaoRecusadas() {
        assertThrows(TransicaoExecucaoInvalidaException.class,
                () -> ordem.adicionarServico(UUID.randomUUID(), "X", Dinheiro.ZERO));
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> ordem.concluirDiagnostico(AGORA));
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> ordem.liberarParaReparo(null, AGORA));
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> ordem.iniciarReparo("m", AGORA));
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> ordem.finalizarReparo(AGORA));
        ordem.iniciarDiagnostico("joao", AGORA);
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> ordem.iniciarDiagnostico("joao", AGORA));
    }

    @Test
    void naoConcluiSemItensNemComPecaSemReserva() {
        ordem.iniciarDiagnostico("joao", AGORA);
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> ordem.concluirDiagnostico(AGORA));

        ordem.adicionarPeca(UUID.randomUUID(), "Pastilha", 1, Dinheiro.ZERO);
        assertThrows(IllegalStateException.class, () -> ordem.concluirDiagnostico(AGORA));
    }

    @Test
    void removerItem() {
        ItemExecucao peca = comDiagnosticoEmAndamento();
        ordem.removerItem(peca.id());
        assertEquals(1, ordem.itens().size());
        assertThrows(ItemExecucaoNaoEncontradoException.class, () -> ordem.removerItem(peca.id()));
        assertThrows(ItemExecucaoNaoEncontradoException.class, () -> ordem.registrarReserva(peca.id(), ReservaId.novo()));
    }

    @Test
    void cancelarEIdempotenteMasNaoDesfazFinalizacao() {
        ordem.cancelar("ORCAMENTO_RECUSADO", AGORA);
        ordem.cancelar("OUTRO", AGORA.plusHours(1));
        assertEquals(StatusExecucao.CANCELADA, ordem.status());
        assertEquals("ORCAMENTO_RECUSADO", ordem.motivoCancelamento());

        OrdemExecucao.Dados d = new OrdemExecucao.Dados();
        d.osId = OrdemServicoId.novo();
        d.veiculo = ordem.veiculo();
        d.status = StatusExecucao.FINALIZADA;
        d.entrouNaFilaEm = AGORA;
        OrdemExecucao finalizada = OrdemExecucao.reconstituir(d);
        assertThrows(TransicaoExecucaoInvalidaException.class, () -> finalizada.cancelar("X", AGORA));
    }

    @Test
    void itemValidaSeusDados() {
        UUID ref = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> ItemExecucao.reconstituir(UUID.randomUUID(), ItemExecucao.Tipo.PECA, ref, " ", 1, Dinheiro.ZERO, null));
        assertThrows(IllegalArgumentException.class,
                () -> ItemExecucao.reconstituir(UUID.randomUUID(), ItemExecucao.Tipo.PECA, ref, "x", 0, Dinheiro.ZERO, null));
        ItemExecucao servico = ItemExecucao.reconstituir(UUID.randomUUID(), ItemExecucao.Tipo.SERVICO, ref, "x", 1,
                Dinheiro.ZERO, null);
        assertThrows(IllegalStateException.class, () -> servico.registrarReserva(ReservaId.novo()));
        assertTrue(EventoExecucao.ordemExecucaoCancelada("x", 0).routingKey().endsWith("ordem-execucao-cancelada"));
    }
}
