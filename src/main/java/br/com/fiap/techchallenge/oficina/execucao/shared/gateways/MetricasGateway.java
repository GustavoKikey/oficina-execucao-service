package br.com.fiap.techchallenge.oficina.execucao.shared.gateways;

import java.time.Duration;

/**
 * Métricas de negócio da Execução. O adapter é <i>best-effort</i>: falha de telemetria
 * nunca derruba operação de negócio (mesma regra da fase 3).
 */
public interface MetricasGateway {

    /** Tempo do mecânico em cada etapa: {@code diagnostico} e {@code reparo}. */
    void etapaConcluida(String etapa, Duration duracao);

    void falhaProcessamento(String operacao);

    void falhaIntegracao(String integracao);
}
