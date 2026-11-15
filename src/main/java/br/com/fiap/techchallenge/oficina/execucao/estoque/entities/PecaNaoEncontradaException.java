package br.com.fiap.techchallenge.oficina.execucao.estoque.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;

public class PecaNaoEncontradaException extends DomainException {

    public PecaNaoEncontradaException(PecaId id) {
        super("Peça não encontrada: " + id);
    }
}
