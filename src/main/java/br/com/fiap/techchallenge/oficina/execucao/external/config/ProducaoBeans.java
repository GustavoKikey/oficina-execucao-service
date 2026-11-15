package br.com.fiap.techchallenge.oficina.execucao.external.config;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.controllers.ServicoController;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.controllers.ProducaoController;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.OrdemExecucaoGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.PublicadorEventosGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.usecases.ComandosSagaUseCase;
import br.com.fiap.techchallenge.oficina.execucao.producao.usecases.TrabalhoMecanicoUseCase;
import br.com.fiap.techchallenge.oficina.execucao.shared.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.usecases.ExecutorTransacional;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;

/** Composição dos controllers de catálogo e produção (os use cases são Java puro). */
@ApplicationScoped
public class ProducaoBeans {

    @Produces
    @ApplicationScoped
    ServicoController servicoController(ServicoGateway gateway, ExecutorTransacional tx) {
        return new ServicoController(gateway, tx);
    }

    /**
     * @param validadeReserva quanto a peça fica reservada esperando o cliente aprovar e
     *                        pagar (saga.md §4). Na demonstração do estorno, reduzir para
     *                        minutos: {@code RESERVA_VALIDADE=PT2M}.
     */
    @Produces
    @ApplicationScoped
    ProducaoController producaoController(
            OrdemExecucaoGateway ordens, ServicoGateway servicos, PecaGateway pecas,
            PublicadorEventosGateway eventos, ExecutorTransacional tx, MetricasGateway metricas, Clock relogio,
            @ConfigProperty(name = "oficina.reserva.validade", defaultValue = "PT48H") Duration validadeReserva) {
        return new ProducaoController(
                new TrabalhoMecanicoUseCase(ordens, servicos, pecas, eventos, validadeReserva, relogio),
                new ComandosSagaUseCase(ordens, pecas, eventos, relogio),
                tx, metricas);
    }

    /** Relógio do sistema; {@code @DefaultBean} para os testes poderem trocá-lo. */
    @Produces
    @Singleton
    @DefaultBean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
