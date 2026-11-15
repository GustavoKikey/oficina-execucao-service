package br.com.fiap.techchallenge.oficina.execucao.catalogo.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/** Record puro de resposta; a montagem vive no {@code ServicoPresenter}. */
public record ServicoResponse(
        UUID id,
        String descricao,
        BigDecimal valorBase
) {
}
