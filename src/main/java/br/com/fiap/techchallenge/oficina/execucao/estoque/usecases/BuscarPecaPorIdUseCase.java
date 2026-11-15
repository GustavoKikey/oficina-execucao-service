package br.com.fiap.techchallenge.oficina.execucao.estoque.usecases;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;

public class BuscarPecaPorIdUseCase {

    private final PecaGateway repository;

    public BuscarPecaPorIdUseCase(PecaGateway repository) {
        this.repository = repository;
    }

    public Peca executar(PecaId id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new PecaNaoEncontradaException(id));
    }
}
