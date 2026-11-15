package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;

import java.util.UUID;

public class ItemExecucaoNaoEncontradoException extends DomainException {

    public ItemExecucaoNaoEncontradoException(UUID itemId) {
        super("Item " + itemId + " não encontrado no diagnóstico");
    }
}
