package br.com.fiap.techchallenge.oficina.execucao.catalogo.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;

public class ServicoNaoEncontradoException extends DomainException {

    public ServicoNaoEncontradoException(ServicoId id) {
        super("Serviço não encontrado: " + id);
    }
}
