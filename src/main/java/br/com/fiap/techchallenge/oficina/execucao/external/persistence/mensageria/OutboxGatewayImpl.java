package br.com.fiap.techchallenge.oficina.execucao.external.persistence.mensageria;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.PublicadorEventosGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grava eventos no outbox, já no formato do envelope do contrato
 * ({@code contratos-mensagens.md} §2), e captura o contexto de trace corrente.
 */
@ApplicationScoped
public class OutboxGatewayImpl implements PublicadorEventosGateway {

    static final String ORIGEM = "execucao-service";

    private final EntityManager em;
    private final ObjectMapper json;
    private final Clock relogio;

    public OutboxGatewayImpl(EntityManager em, ObjectMapper json, Clock relogio) {
        this.em = em;
        this.json = json;
        this.relogio = relogio;
    }

    @Override
    public void publicar(List<EventoExecucao> eventos, OrdemServicoId correlationId, UUID causationId) {
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        String headers = escrever(contextoDeTrace());
        for (EventoExecucao evento : eventos) {
            UUID messageId = UUID.randomUUID();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("messageId", messageId.toString());
            envelope.put("type", evento.tipo());
            envelope.put("version", 1);
            envelope.put("correlationId", correlationId.valor().toString());
            envelope.put("causationId", causationId == null ? null : causationId.toString());
            envelope.put("occurredAt", agora.toInstant().toString());
            envelope.put("source", ORIGEM);
            envelope.put("payload", evento.payload());

            OutboxJpaEntity linha = new OutboxJpaEntity();
            linha.messageId = messageId;
            linha.tipo = evento.tipo();
            linha.routingKey = evento.routingKey();
            linha.correlationId = correlationId.valor();
            linha.envelope = escrever(envelope);
            linha.headers = headers;
            linha.criadoEm = agora;
            em.persist(linha);
        }
    }

    /**
     * {@code traceparent} da requisição/mensagem que está sendo tratada. O relay publica
     * em outra thread, sem esse contexto — sem guardá-lo aqui, o trace distribuído se
     * partiria em dois exatamente na fila.
     */
    private static Map<String, String> contextoDeTrace() {
        Map<String, String> headers = new LinkedHashMap<>();
        W3CTraceContextPropagator.getInstance().inject(Context.current(), headers, Map::put);
        return headers;
    }

    private String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar mensagem do outbox", e);
        }
    }
}
