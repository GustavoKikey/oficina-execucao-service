package br.com.fiap.techchallenge.oficina.execucao.external.config;

import br.com.fiap.techchallenge.oficina.execucao.estoque.controllers.PecaController;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.usecases.ExecutorTransacional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Composition root do BC Estoque. */
@ApplicationScoped
public class EstoqueBeans {

    @Produces
    @ApplicationScoped
    PecaController pecaController(PecaGateway gateway, ExecutorTransacional tx) {
        return new PecaController(gateway, tx);
    }
}
