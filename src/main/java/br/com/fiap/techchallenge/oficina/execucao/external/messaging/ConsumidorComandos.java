package br.com.fiap.techchallenge.oficina.execucao.external.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.concurrent.CompletionStage;

/**
 * Porta de entrada dos comandos do orquestrador (fila {@code execucao.comandos}).
 *
 * <p>Valida o envelope e põe o {@code correlationId} (= id da OS) no MDC, para que todo
 * log do processamento seja encontrável pela OS. Envelope inválido é rejeitado e vai
 * para a DLQ ({@code execucao.comandos.dlq}).
 *
 * <p>Esqueleto da etapa 2: o despacho para os handlers idempotentes de cada comando
 * entra na etapa 3.
 */
@ApplicationScoped
public class ConsumidorComandos {

    private static final Logger LOG = Logger.getLogger(ConsumidorComandos.class);

    @Incoming("comandos")
    public CompletionStage<Void> receber(Message<?> mensagem) {
        Envelope envelope;
        try {
            envelope = Envelope.de(mensagem.getPayload());
        } catch (EnvelopeInvalidoException e) {
            LOG.errorf("Comando rejeitado (vai para a DLQ): %s", e.getMessage());
            return mensagem.nack(e);
        }

        MDC.put("correlationId", envelope.correlationId().toString());
        try {
            LOG.infof("Comando recebido: type=%s v%d messageId=%s source=%s",
                    envelope.type(), envelope.version(), envelope.messageId(), envelope.source());
            return mensagem.ack();
        } finally {
            MDC.remove("correlationId");
        }
    }
}
