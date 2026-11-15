package br.com.fiap.techchallenge.oficina.execucao.producao.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** Itens do diagnóstico. Serviços vêm do catálogo; peças, do estoque — ambos deste serviço. */
public final class AdicionarItemRequest {

    private AdicionarItemRequest() {}

    public record Servico(
            @Schema(description = "ID do serviço no catálogo") @NotNull UUID servicoId,
            @Schema(example = "120.00", description = "Valor cobrado — se omitido, usa o valor base do catálogo")
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal valorCobrado) {}

    public record Peca(
            @Schema(description = "ID da peça no estoque") @NotNull UUID pecaId,
            @Schema(example = "2") @NotNull @Positive Integer quantidade) {}
}
