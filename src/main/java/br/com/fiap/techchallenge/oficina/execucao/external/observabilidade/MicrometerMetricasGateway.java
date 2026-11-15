package br.com.fiap.techchallenge.oficina.execucao.external.observabilidade;

import br.com.fiap.techchallenge.oficina.execucao.shared.gateways.MetricasGateway;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Métricas expostas em {@code /q/metrics} e coletadas pelo agente New Relic:
 * {@code oficina_execucao_etapa_duracao_seconds{etapa}}, {@code oficina_execucao_falhas_total{operacao}}
 * e {@code oficina_integracao_falhas_total{integracao}}.
 */
@ApplicationScoped
public class MicrometerMetricasGateway implements MetricasGateway {

    private static final Logger LOG = Logger.getLogger(MicrometerMetricasGateway.class);

    private final MeterRegistry registry;

    public MicrometerMetricasGateway(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void etapaConcluida(String etapa, Duration duracao) {
        if (etapa == null || duracao == null || duracao.isNegative()) {
            return;
        }
        seguro(() -> Timer.builder("oficina.execucao.etapa.duracao")
                .description("Tempo do mecânico em cada etapa (diagnóstico, reparo)")
                .tag("etapa", etapa)
                .register(registry)
                .record(duracao));
    }

    @Override
    public void falhaProcessamento(String operacao) {
        seguro(() -> registry.counter("oficina.execucao.falhas", "operacao", nuloVira(operacao)).increment());
    }

    @Override
    public void falhaIntegracao(String integracao) {
        seguro(() -> registry.counter("oficina.integracao.falhas", "integracao", nuloVira(integracao)).increment());
    }

    private static void seguro(Runnable registro) {
        try {
            registro.run();
        } catch (RuntimeException e) {
            LOG.warnf(e, "Falha ao registrar métrica — operação de negócio não afetada.");
        }
    }

    private static String nuloVira(String valor) {
        return valor == null ? "desconhecida" : valor;
    }
}
