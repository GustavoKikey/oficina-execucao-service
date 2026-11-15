package br.com.fiap.techchallenge.oficina.execucao.catalogo.controllers;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.dtos.AtualizarServicoRequest;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.dtos.CadastrarServicoRequest;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.dtos.ServicoResponse;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.presenters.ServicoPresenter;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases.AtualizarServicoUseCase;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases.BuscarServicoPorIdUseCase;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases.CadastrarServicoUseCase;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases.ListarServicosUseCase;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases.RemoverServicoUseCase;
import br.com.fiap.techchallenge.oficina.execucao.shared.usecases.ExecutorTransacional;

import java.util.List;
import java.util.UUID;

/** Controller do agregado Serviço (catálogo de mão-de-obra). */
public class ServicoController {

    private final CadastrarServicoUseCase cadastrar;
    private final AtualizarServicoUseCase atualizar;
    private final RemoverServicoUseCase remover;
    private final BuscarServicoPorIdUseCase buscar;
    private final ListarServicosUseCase listar;
    private final ExecutorTransacional tx;
    private final ServicoPresenter presenter = new ServicoPresenter();

    public ServicoController(ServicoGateway gateway, ExecutorTransacional tx) {
        this.cadastrar = new CadastrarServicoUseCase(gateway);
        this.atualizar = new AtualizarServicoUseCase(gateway);
        this.remover = new RemoverServicoUseCase(gateway);
        this.buscar = new BuscarServicoPorIdUseCase(gateway);
        this.listar = new ListarServicosUseCase(gateway);
        this.tx = tx;
    }

    public ServicoResponse cadastrar(CadastrarServicoRequest req) {
        Servico s = tx.emTransacao(() -> cadastrar.executar(
                new CadastrarServicoUseCase.Input(req.descricao(), req.valorBase())));
        return presenter.apresentar(s);
    }

    public List<ServicoResponse> listar() {
        return presenter.apresentar(tx.emTransacao(listar::executar));
    }

    public ServicoResponse buscar(UUID id) {
        return presenter.apresentar(tx.emTransacao(() -> buscar.executar(ServicoId.de(id))));
    }

    public ServicoResponse atualizar(UUID id, AtualizarServicoRequest req) {
        Servico s = tx.emTransacao(() -> atualizar.executar(new AtualizarServicoUseCase.Input(
                ServicoId.de(id), req.descricao(), req.valorBase())));
        return presenter.apresentar(s);
    }

    public void remover(UUID id) {
        tx.emTransacao(() -> {
            remover.executar(ServicoId.de(id));
            return null;
        });
    }
}
