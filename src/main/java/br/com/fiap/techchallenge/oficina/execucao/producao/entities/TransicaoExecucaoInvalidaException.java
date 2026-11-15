package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;

/** Ação do mecânico fora da etapa certa (ex.: concluir diagnóstico sem tê-lo iniciado) — 409. */
public class TransicaoExecucaoInvalidaException extends DomainException {

    public TransicaoExecucaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
