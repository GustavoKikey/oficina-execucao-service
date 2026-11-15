package br.com.fiap.techchallenge.oficina.execucao.external.messaging;

import br.com.fiap.techchallenge.oficina.execucao.producao.controllers.ProducaoController;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.usecases.ComandosSagaUseCase;
import br.com.fiap.techchallenge.oficina.execucao.shared.gateways.MetricasGateway;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumidorComandosTest {

    private static final EventoExecucao RESPOSTA = new EventoExecucao("Resposta", Map.of());

    private ProducaoController producao;
    private MetricasGateway metricas;
    private ConsumidorComandos consumidor;
    private final AtomicBoolean confirmada = new AtomicBoolean();
    private final AtomicReference<Throwable> rejeitada = new AtomicReference<>();

    @BeforeEach
    void preparar() {
        producao = mock(ProducaoController.class);
        metricas = mock(MetricasGateway.class);
        consumidor = new ConsumidorComandos(producao, metricas, 3, 0);
    }

    private Message<Object> mensagem(Object corpo) {
        return Message.of(corpo,
                () -> {
                    confirmada.set(true);
                    return CompletableFuture.completedFuture(null);
                },
                erro -> {
                    rejeitada.set(erro);
                    return CompletableFuture.completedFuture(null);
                });
    }

    private static JsonObject comando(String tipo, JsonObject payload) {
        return EnvelopeTest.envelopeValido().put("type", tipo).put("payload", payload);
    }

    @Test
    void abrirOrdemExecucaoComDadosDoVeiculo() {
        when(producao.abrirOrdemExecucao(any())).thenReturn(RESPOSTA);
        JsonObject cmd = comando("AbrirOrdemExecucao", new JsonObject()
                .put("veiculo", new JsonObject().put("placa", "ABC1D23").put("marca", "Fiat").put("ano", 2022))
                .put("relatoCliente", "Barulho"));

        consumidor.receber(mensagem(Buffer.buffer(cmd.encode()))).toCompletableFuture().join();

        ArgumentCaptor<ComandosSagaUseCase.AbrirOrdemExecucao> c =
                ArgumentCaptor.forClass(ComandosSagaUseCase.AbrirOrdemExecucao.class);
        verify(producao).abrirOrdemExecucao(c.capture());
        assertEquals("ABC1D23", c.getValue().veiculo().placa());
        assertEquals(2022, c.getValue().veiculo().ano());
        assertNull(c.getValue().veiculo().modelo());
        assertEquals(UUID.fromString(cmd.getString("correlationId")), c.getValue().osId().valor());
        assertTrue(confirmada.get());
    }

    @Test
    void liberarECancelar() {
        when(producao.liberarParaReparo(any())).thenReturn(RESPOSTA);
        when(producao.cancelarOrdemExecucao(any())).thenReturn(RESPOSTA);
        UUID orcamento = UUID.randomUUID();

        consumidor.receber(mensagem(comando("LiberarParaReparo",
                new JsonObject().put("orcamentoId", orcamento.toString())))).toCompletableFuture().join();
        consumidor.receber(mensagem(comando("CancelarOrdemExecucao",
                new JsonObject().put("motivo", "PAGAMENTO_RECUSADO")))).toCompletableFuture().join();

        ArgumentCaptor<ComandosSagaUseCase.LiberarParaReparo> l =
                ArgumentCaptor.forClass(ComandosSagaUseCase.LiberarParaReparo.class);
        verify(producao).liberarParaReparo(l.capture());
        assertEquals(orcamento, l.getValue().orcamentoId());
        ArgumentCaptor<ComandosSagaUseCase.CancelarOrdemExecucao> c =
                ArgumentCaptor.forClass(ComandosSagaUseCase.CancelarOrdemExecucao.class);
        verify(producao).cancelarOrdemExecucao(c.capture());
        assertEquals("PAGAMENTO_RECUSADO", c.getValue().motivo());
    }

    @Test
    void comandoDeOutraFilaEIgnorado() {
        consumidor.receber(mensagem(comando("GerarOrcamento", new JsonObject()))).toCompletableFuture().join();

        assertTrue(confirmada.get());
        verify(producao, never()).abrirOrdemExecucao(any());
    }

    @Test
    void payloadOuEnvelopeInvalidoVaiParaADlq() {
        consumidor.receber(mensagem(comando("AbrirOrdemExecucao", new JsonObject()))).toCompletableFuture().join();
        assertInstanceOf(EnvelopeInvalidoException.class, rejeitada.get());

        rejeitada.set(null);
        consumidor.receber(mensagem(comando("LiberarParaReparo", new JsonObject().put("orcamentoId", "x"))))
                .toCompletableFuture().join();
        assertInstanceOf(EnvelopeInvalidoException.class, rejeitada.get());

        rejeitada.set(null);
        consumidor.receber(mensagem("{}")).toCompletableFuture().join();
        assertInstanceOf(EnvelopeInvalidoException.class, rejeitada.get());
        verify(metricas, times(3)).falhaIntegracao("rabbitmq-comando-invalido");
    }

    @Test
    void falhaTecnicaRetentaEDepoisVaiParaADlq() {
        when(producao.cancelarOrdemExecucao(any()))
                .thenThrow(new IllegalStateException("lock"))
                .thenReturn(RESPOSTA);
        consumidor.receber(mensagem(comando("CancelarOrdemExecucao", new JsonObject()))).toCompletableFuture().join();
        assertTrue(confirmada.get());

        confirmada.set(false);
        when(producao.liberarParaReparo(any())).thenThrow(new IllegalStateException("banco fora"));
        consumidor.receber(mensagem(comando("LiberarParaReparo", new JsonObject()))).toCompletableFuture().join();
        assertFalse(confirmada.get());
        verify(producao, times(3)).liberarParaReparo(any());
        verify(metricas).falhaProcessamento("comando-LiberarParaReparo");
    }
}
