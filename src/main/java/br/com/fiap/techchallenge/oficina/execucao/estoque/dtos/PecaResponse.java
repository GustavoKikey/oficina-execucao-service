package br.com.fiap.techchallenge.oficina.execucao.estoque.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/** Record puro de resposta; a montagem vive no {@code EstoquePresenter}. */
public record PecaResponse(
        UUID id,
        String descricao,
        BigDecimal valorUnitario,
        int quantidadeTotal,
        int saldoDisponivel
) {
}
