package br.com.fiap.techchallenge.oficina.execucao.catalogo.presenters;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.dtos.ServicoResponse;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;

import java.util.List;

/** Presenter do agregado Serviço. */
public class ServicoPresenter {

    public ServicoResponse apresentar(Servico s) {
        return new ServicoResponse(
                s.id().valor(),
                s.descricao(),
                s.valorBase().valor());
    }

    public List<ServicoResponse> apresentar(List<Servico> servicos) {
        return servicos.stream().map(this::apresentar).toList();
    }
}
