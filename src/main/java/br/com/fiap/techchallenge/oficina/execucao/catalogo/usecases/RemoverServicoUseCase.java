package br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;

public class RemoverServicoUseCase {

    private final ServicoGateway repository;

    public RemoverServicoUseCase(ServicoGateway repository) {
        this.repository = repository;
    }

    public void executar(ServicoId id) {
        if (repository.buscarPorId(id).isEmpty()) {
            throw new ServicoNaoEncontradoException(id);
        }
        repository.remover(id);
    }
}
