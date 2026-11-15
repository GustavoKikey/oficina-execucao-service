package br.com.fiap.techchallenge.oficina.execucao.catalogo.usecases;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;

import java.math.BigDecimal;

public class CadastrarServicoUseCase {

    private final ServicoGateway repository;

    public CadastrarServicoUseCase(ServicoGateway repository) {
        this.repository = repository;
    }

    public record Input(String descricao, BigDecimal valorBase) {}

    public Servico executar(Input input) {
        Servico servico = Servico.novo(input.descricao(), Dinheiro.de(input.valorBase()));
        return repository.salvar(servico);
    }
}
