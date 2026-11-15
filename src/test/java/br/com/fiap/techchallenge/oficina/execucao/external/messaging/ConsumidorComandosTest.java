package br.com.fiap.techchallenge.oficina.execucao.external.messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumidorComandosTest {

    private final ConsumidorComandos consumidor = new ConsumidorComandos();

    private final AtomicBoolean confirmada = new AtomicBoolean();
    private final AtomicReference<Throwable> rejeitada = new AtomicReference<>();

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

    @Test
    void confirmaComandoComEnvelopeValido() {
        consumidor.receber(mensagem(EnvelopeTest.envelopeValido())).toCompletableFuture().join();

        assertTrue(confirmada.get());
        assertNull(rejeitada.get());
    }

    @Test
    void rejeitaComandoComEnvelopeInvalidoParaIrADlq() {
        consumidor.receber(mensagem("{\"type\":\"OrcamentoGerado\"}")).toCompletableFuture().join();

        assertFalse(confirmada.get());
        assertInstanceOf(EnvelopeInvalidoException.class, rejeitada.get());
    }
}
