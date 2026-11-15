package br.com.fiap.techchallenge.oficina.execucao.estoque.usecases;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;

import java.math.BigDecimal;

public class AtualizarPecaUseCase {

    private final PecaGateway repository;

    public AtualizarPecaUseCase(PecaGateway repository) {
        this.repository = repository;
    }

    public record Input(PecaId id, String descricao, BigDecimal valorUnitario) {}

    public Peca executar(Input input) {
        Peca peca = repository.buscarPorId(input.id())
                .orElseThrow(() -> new PecaNaoEncontradaException(input.id()));
        peca.alterar(input.descricao(), Dinheiro.de(input.valorUnitario()));
        return repository.salvar(peca);
    }
}
