package br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;

import java.util.List;

public class ListarServicosUseCase {

    private final ServicoGateway repository;

    public ListarServicosUseCase(ServicoGateway repository) {
        this.repository = repository;
    }

    public List<Servico> executar() {
        return repository.listar();
    }
}
