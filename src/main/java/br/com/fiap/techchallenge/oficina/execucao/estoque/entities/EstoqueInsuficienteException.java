package br.com.fiap.techchallenge.oficina.execucao.estoque.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;

public class EstoqueInsuficienteException extends DomainException {

    public EstoqueInsuficienteException(PecaId pecaId, int solicitado, int disponivel) {
        super("Estoque insuficiente para peça " + pecaId
                + ": solicitado " + solicitado + ", disponível " + disponivel);
    }
}
