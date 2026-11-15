package br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;

public class BuscarServicoPorIdUseCase {

    private final ServicoGateway repository;

    public BuscarServicoPorIdUseCase(ServicoGateway repository) {
        this.repository = repository;
    }

    public Servico executar(ServicoId id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new ServicoNaoEncontradoException(id));
    }
}
