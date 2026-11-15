package br.com.fiap.techchallenge.oficina.execucao.external.messaging;

import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.usecases.ComandosSagaUseCase;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Traduz o payload dos comandos da saga ({@code contratos-mensagens.md} §3) para os
 * tipos do use case. O {@code osId} vem do {@code correlationId} do envelope, que é
 * sempre o id da OS; payload malformado é defeito do publicador e vai para a DLQ.
 */
public final class ComandoMapper {

    private ComandoMapper() {}

    public static ComandosSagaUseCase.AbrirOrdemExecucao abrir(Envelope e) {
        JsonNode v = obrigatorio(e.payload(), "veiculo");
        JsonNode ano = v.get("ano");
        return new ComandosSagaUseCase.AbrirOrdemExecucao(e.messageId(), osId(e),
                new OrdemExecucao.Veiculo(obrigatorio(v, "placa").asText(), texto(v, "marca"), texto(v, "modelo"),
                        ano == null || ano.isNull() ? null : ano.asInt()),
                texto(e.payload(), "relatoCliente"));
    }

    public static ComandosSagaUseCase.LiberarParaReparo liberar(Envelope e) {
        String orcamento = texto(e.payload(), "orcamentoId");
        try {
            return new ComandosSagaUseCase.LiberarParaReparo(e.messageId(), osId(e),
                    orcamento == null ? null : UUID.fromString(orcamento));
        } catch (IllegalArgumentException ex) {
            throw new EnvelopeInvalidoException("orcamentoId não é um UUID: " + orcamento);
        }
    }

    public static ComandosSagaUseCase.CancelarOrdemExecucao cancelar(Envelope e) {
        return new ComandosSagaUseCase.CancelarOrdemExecucao(e.messageId(), osId(e), texto(e.payload(), "motivo"));
    }

    private static OrdemServicoId osId(Envelope e) {
        return OrdemServicoId.de(e.correlationId());
    }

    private static JsonNode obrigatorio(JsonNode no, String campo) {
        JsonNode valor = no == null ? null : no.get(campo);
        if (valor == null || valor.isNull()) {
            throw new EnvelopeInvalidoException("campo obrigatório ausente no payload: " + campo);
        }
        return valor;
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no == null ? null : no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }
}
