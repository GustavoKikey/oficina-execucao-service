package br.com.fiap.techchallenge.oficina.execucao.producao.gateways;

import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

import java.util.List;
import java.util.UUID;

/**
 * Outbox transacional (ADR 010): grava os eventos na mesma transação do estado; o relay
 * publica depois do commit.
 */
public interface PublicadorEventosGateway {

    /** @param causationId comando que causou os eventos; nulo para ação do mecânico */
    void publicar(List<EventoExecucao> eventos, OrdemServicoId correlationId, UUID causationId);

    default void publicar(EventoExecucao evento, OrdemServicoId correlationId, UUID causationId) {
        publicar(List.of(evento), correlationId, causationId);
    }
}
