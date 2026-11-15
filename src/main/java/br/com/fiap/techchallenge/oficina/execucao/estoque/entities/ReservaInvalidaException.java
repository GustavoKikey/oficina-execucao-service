package br.com.fiap.techchallenge.oficina.execucao.estoque.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;

public class ReservaInvalidaException extends DomainException {

    public ReservaInvalidaException(String message) {
        super(message);
    }
}
