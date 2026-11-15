package br.com.fiap.techchallenge.oficina.execucao.producao.presenters;

import br.com.fiap.techchallenge.oficina.execucao.producao.dtos.OrdemExecucaoResponse;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.ItemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;

import java.util.List;

public class OrdemExecucaoPresenter {

    public OrdemExecucaoResponse apresentar(OrdemExecucao o) {
        OrdemExecucao.Veiculo v = o.veiculo();
        return new OrdemExecucaoResponse(
                o.osId().valor(),
                o.status().name(),
                o.status().etapa() == null ? null : o.status().etapa().name(),
                new OrdemExecucaoResponse.VeiculoView(v.placa(), v.marca(), v.modelo(), v.ano()),
                o.relatoCliente(),
                o.itens().stream().map(this::view).toList(),
                o.total().valor(),
                o.orcamentoId(),
                o.mecanicoDiagnostico(),
                o.mecanicoReparo(),
                o.motivoCancelamento(),
                o.entrouNaFilaEm(),
                o.diagnosticoIniciadoEm(),
                o.diagnosticoConcluidoEm(),
                o.liberadaParaReparoEm(),
                o.reparoIniciadoEm(),
                o.finalizadaEm(),
                o.canceladaEm());
    }

    public List<OrdemExecucaoResponse> apresentar(List<OrdemExecucao> ordens) {
        return ordens.stream().map(this::apresentar).toList();
    }

    private OrdemExecucaoResponse.ItemView view(ItemExecucao i) {
        return new OrdemExecucaoResponse.ItemView(i.id(), i.tipo().name(), i.referenciaId(), i.descricao(),
                i.quantidade(), i.valorUnitario().valor(), i.subtotal().valor(),
                i.reservaId() == null ? null : i.reservaId().valor());
    }
}
