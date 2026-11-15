package br.com.fiap.techchallenge.oficina.execucao.estoque.usecases;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaPossuiReservasAtivasException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;

public class RemoverPecaUseCase {

    private final PecaGateway repository;

    public RemoverPecaUseCase(PecaGateway repository) {
        this.repository = repository;
    }

    public void executar(PecaId id) {
        Peca peca = repository.buscarPorId(id)
                .orElseThrow(() -> new PecaNaoEncontradaException(id));
        if (peca.possuiReservasAtivas()) {
            throw new PecaPossuiReservasAtivasException(id);
        }
        repository.remover(id);
    }
}
