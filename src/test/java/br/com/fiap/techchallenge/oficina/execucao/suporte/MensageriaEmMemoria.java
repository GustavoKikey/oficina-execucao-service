package br.com.fiap.techchallenge.oficina.execucao.suporte;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * Troca o RabbitMQ pelo conector em memória: o teste faz o papel do orquestrador —
 * envia comandos ({@code comandos}) e lê as respostas publicadas pelo relay ({@code eventos}).
 */
public class MensageriaEmMemoria implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> propriedades = new HashMap<>();
        propriedades.putAll(InMemoryConnector.switchIncomingChannelsToInMemory("comandos"));
        propriedades.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("eventos"));
        return propriedades;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
