package br.com.fiap.techchallenge.oficina.execucao.producao.controllers;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.producao.dtos.AdicionarItemRequest;
import br.com.fiap.techchallenge.oficina.execucao.producao.dtos.OrdemExecucaoResponse;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.shared.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.StatusExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.presenters.OrdemExecucaoPresenter;
import br.com.fiap.techchallenge.oficina.execucao.producao.usecases.ComandosSagaUseCase;
import br.com.fiap.techchallenge.oficina.execucao.producao.usecases.TrabalhoMecanicoUseCase;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.execucao.shared.usecases.ExecutorTransacional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Interface Adapter da produção: fronteira de transação para as ações do mecânico (API)
 * e para os comandos da saga (mensageria).
 */
public class ProducaoController {

    private final TrabalhoMecanicoUseCase mecanico;
    private final ComandosSagaUseCase comandos;
    private final ExecutorTransacional tx;
    private final MetricasGateway metricas;
    private final OrdemExecucaoPresenter presenter = new OrdemExecucaoPresenter();

    public ProducaoController(TrabalhoMecanicoUseCase mecanico, ComandosSagaUseCase comandos, ExecutorTransacional tx,
                              MetricasGateway metricas) {
        this.mecanico = mecanico;
        this.comandos = comandos;
        this.tx = tx;
        this.metricas = metricas;
    }

    // ---------- mecânico ----------

    public List<OrdemExecucaoResponse> fila(String etapa) {
        StatusExecucao.Etapa e = etapa == null ? StatusExecucao.Etapa.DIAGNOSTICO
                : StatusExecucao.Etapa.valueOf(etapa.toUpperCase());
        return presenter.apresentar(tx.emTransacao(() -> mecanico.fila(e)));
    }

    public OrdemExecucaoResponse buscar(UUID osId) {
        return presenter.apresentar(tx.emTransacao(() -> mecanico.buscar(OrdemServicoId.de(osId))));
    }

    public OrdemExecucaoResponse iniciarDiagnostico(UUID osId, String usuario) {
        return presenter.apresentar(tx.emTransacao(() -> mecanico.iniciarDiagnostico(OrdemServicoId.de(osId), usuario)));
    }

    public OrdemExecucaoResponse adicionarServico(UUID osId, AdicionarItemRequest.Servico req) {
        return presenter.apresentar(tx.emTransacao(() -> mecanico.adicionarServico(OrdemServicoId.de(osId),
                ServicoId.de(req.servicoId()), req.valorCobrado())));
    }

    public OrdemExecucaoResponse adicionarPeca(UUID osId, AdicionarItemRequest.Peca req) {
        return presenter.apresentar(tx.emTransacao(() -> mecanico.adicionarPeca(OrdemServicoId.de(osId),
                PecaId.de(req.pecaId()), req.quantidade())));
    }

    public OrdemExecucaoResponse removerItem(UUID osId, UUID itemId) {
        return presenter.apresentar(tx.emTransacao(() -> mecanico.removerItem(OrdemServicoId.de(osId), itemId)));
    }

    public OrdemExecucaoResponse concluirDiagnostico(UUID osId) {
        OrdemExecucao o = tx.emTransacao(() -> mecanico.concluirDiagnostico(OrdemServicoId.de(osId)));
        metricas.etapaConcluida("diagnostico", Duration.between(o.diagnosticoIniciadoEm(), o.diagnosticoConcluidoEm()));
        return presenter.apresentar(o);
    }

    public OrdemExecucaoResponse iniciarReparo(UUID osId, String usuario) {
        return presenter.apresentar(tx.emTransacao(() -> mecanico.iniciarReparo(OrdemServicoId.de(osId), usuario)));
    }

    public OrdemExecucaoResponse finalizarReparo(UUID osId) {
        OrdemExecucao o = tx.emTransacao(() -> mecanico.finalizarReparo(OrdemServicoId.de(osId)));
        metricas.etapaConcluida("reparo", Duration.between(o.reparoIniciadoEm(), o.finalizadaEm()));
        return presenter.apresentar(o);
    }

    // ---------- comandos da saga ----------

    public EventoExecucao abrirOrdemExecucao(ComandosSagaUseCase.AbrirOrdemExecucao cmd) {
        return tx.emTransacao(() -> comandos.abrir(cmd));
    }

    public EventoExecucao liberarParaReparo(ComandosSagaUseCase.LiberarParaReparo cmd) {
        return tx.emTransacao(() -> comandos.liberarParaReparo(cmd));
    }

    public EventoExecucao cancelarOrdemExecucao(ComandosSagaUseCase.CancelarOrdemExecucao cmd) {
        return tx.emTransacao(() -> comandos.cancelar(cmd));
    }
}
