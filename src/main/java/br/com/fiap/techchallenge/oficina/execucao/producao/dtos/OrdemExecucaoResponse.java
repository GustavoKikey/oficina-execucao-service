package br.com.fiap.techchallenge.oficina.execucao.producao.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrdemExecucaoResponse(
        UUID osId,
        String status,
        String fila,
        VeiculoView veiculo,
        String relatoCliente,
        List<ItemView> itens,
        BigDecimal total,
        UUID orcamentoId,
        String mecanicoDiagnostico,
        String mecanicoReparo,
        String motivoCancelamento,
        OffsetDateTime entrouNaFilaEm,
        OffsetDateTime diagnosticoIniciadoEm,
        OffsetDateTime diagnosticoConcluidoEm,
        OffsetDateTime liberadaParaReparoEm,
        OffsetDateTime reparoIniciadoEm,
        OffsetDateTime finalizadaEm,
        OffsetDateTime canceladaEm
) {
    public record VeiculoView(String placa, String marca, String modelo, Integer ano) {}

    public record ItemView(UUID id, String tipo, UUID referenciaId, String descricao, int quantidade,
                           BigDecimal valorUnitario, BigDecimal subtotal, UUID reservaId) {}
}
