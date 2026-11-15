package br.com.fiap.techchallenge.oficina.execucao.estoque.usecases;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;

import java.util.List;

public class ListarPecasUseCase {

    private final PecaGateway repository;

    public ListarPecasUseCase(PecaGateway repository) {
        this.repository = repository;
    }

    public List<Peca> executar() {
        return repository.listar();
    }
}
