package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

public class OrdemExecucaoNaoEncontradaException extends DomainException {

    public OrdemExecucaoNaoEncontradaException(OrdemServicoId osId) {
        super("Nenhuma ordem de execução para a OS " + osId);
    }
}
