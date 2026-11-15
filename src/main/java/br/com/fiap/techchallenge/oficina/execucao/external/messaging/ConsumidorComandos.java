package br.com.fiap.techchallenge.oficina.execucao.external.messaging;

import br.com.fiap.techchallenge.oficina.execucao.producao.controllers.ProducaoController;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.shared.gateways.MetricasGateway;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Porta de entrada dos comandos do orquestrador (fila {@code execucao.comandos}).
 *
 * <ul>
 *   <li>envelope ou payload malformado → {@code nack} → DLQ;</li>
 *   <li>tipo desconhecido → {@code ack} (contrato: ignorar o que não é seu);</li>
 *   <li>falha técnica → retentativa com backoff; esgotada, DLQ e alerta.</li>
 * </ul>
 * Cada comando roda numa transação; a resposta vai para o outbox na mesma transação.
 */
@ApplicationScoped
public class ConsumidorComandos {

    private static final Logger LOG = Logger.getLogger(ConsumidorComandos.class);

    private final ProducaoController producao;
    private final MetricasGateway metricas;
    private final int tentativas;
    private final long backoffMs;

    public ConsumidorComandos(ProducaoController producao, MetricasGateway metricas,
                              @ConfigProperty(name = "oficina.mensageria.tentativas", defaultValue = "3") int tentativas,
                              @ConfigProperty(name = "oficina.mensageria.backoff-ms", defaultValue = "200") long backoffMs) {
        this.producao = producao;
        this.metricas = metricas;
        this.tentativas = Math.max(1, tentativas);
        this.backoffMs = backoffMs;
    }

    @Incoming("comandos")
    @Blocking
    public CompletionStage<Void> receber(Message<?> mensagem) {
        Envelope envelope;
        Supplier<EventoExecucao> acao;
        try {
            envelope = Envelope.de(mensagem.getPayload());
            acao = despachar(envelope);
        } catch (EnvelopeInvalidoException e) {
            LOG.errorf("Comando rejeitado (vai para a DLQ): %s", e.getMessage());
            metricas.falhaIntegracao("rabbitmq-comando-invalido");
            return mensagem.nack(e);
        }

        MDC.put("correlationId", envelope.correlationId().toString());
        try {
            if (acao == null) {
                LOG.infof("Comando %s não é desta fila — confirmado e ignorado", envelope.type());
                return mensagem.ack();
            }
            return executarComRetentativa(mensagem, envelope, acao);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private Supplier<EventoExecucao> despachar(Envelope e) {
        return switch (e.type()) {
            case "AbrirOrdemExecucao" -> {
                var cmd = ComandoMapper.abrir(e);
                yield () -> producao.abrirOrdemExecucao(cmd);
            }
            case "LiberarParaReparo" -> {
                var cmd = ComandoMapper.liberar(e);
                yield () -> producao.liberarParaReparo(cmd);
            }
            case "CancelarOrdemExecucao" -> {
                var cmd = ComandoMapper.cancelar(e);
                yield () -> producao.cancelarOrdemExecucao(cmd);
            }
            default -> null;
        };
    }

    private CompletionStage<Void> executarComRetentativa(Message<?> mensagem, Envelope envelope,
                                                         Supplier<EventoExecucao> acao) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                EventoExecucao resposta = acao.get();
                LOG.infof("Comando %s tratado — resposta %s", envelope.type(), resposta.tipo());
                return mensagem.ack();
            } catch (RuntimeException e) {
                if (tentativa >= tentativas) {
                    LOG.errorf(e, "Comando %s (messageId %s) falhou %d vez(es) — vai para a DLQ",
                            envelope.type(), envelope.messageId(), tentativa);
                    metricas.falhaProcessamento("comando-" + envelope.type());
                    return mensagem.nack(e);
                }
                LOG.warnf("Comando %s falhou na tentativa %d/%d (%s) — nova tentativa",
                        envelope.type(), tentativa, tentativas, e.getMessage());
                esperar(backoffMs * (1L << (tentativa - 1)));
            }
        }
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
