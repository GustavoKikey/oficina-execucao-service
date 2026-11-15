package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evento que a Execução publica para o orquestrador ({@code contratos-mensagens.md} §4).
 *
 * <p>Payload em valores simples (texto, número, listas, mapas): a mensageria serializa
 * em JSON e o domínio não depende de Jackson. Dinheiro como texto decimal, instantes em
 * ISO-8601, e todo evento leva o {@code osId}.
 */
public record EventoExecucao(String tipo, Map<String, Object> payload) {

    public EventoExecucao {
        Objects.requireNonNull(tipo, "tipo");
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** Routing key no exchange {@code oficina.eventos}: {@code execucao.<evento-em-kebab-case>}. */
    public String routingKey() {
        return "execucao." + tipo.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    public static EventoExecucao ordemExecucaoAberta(OrdemExecucao o, int posicaoFila) {
        return evento("OrdemExecucaoAberta", o, "posicaoFila", posicaoFila);
    }

    public static EventoExecucao diagnosticoIniciado(OrdemExecucao o) {
        return evento("DiagnosticoIniciado", o,
                "mecanicoId", o.mecanicoDiagnostico(),
                "iniciadoEm", o.diagnosticoIniciadoEm().toString());
    }

    public static EventoExecucao diagnosticoConcluido(OrdemExecucao o, List<Map<String, Object>> reservas) {
        List<Map<String, Object>> itens = o.itens().stream()
                .map(i -> mapa("tipo", i.tipo().name(),
                        "referenciaId", i.referenciaId().toString(),
                        "descricao", i.descricao(),
                        "quantidade", i.quantidade(),
                        "valorUnitario", i.valorUnitario().valor().toPlainString()))
                .toList();
        return evento("DiagnosticoConcluido", o,
                "diagnosticoIniciadoEm", o.diagnosticoIniciadoEm().toString(),
                "concluidoEm", o.diagnosticoConcluidoEm().toString(),
                "itens", itens,
                "reservas", reservas);
    }

    public static EventoExecucao reparoLiberado(OrdemExecucao o) {
        return evento("ReparoLiberado", o, "liberadoEm", o.liberadaParaReparoEm().toString());
    }

    public static EventoExecucao reparoNaoLiberado(OrdemExecucao o, String motivo, List<Map<String, Object>> faltantes) {
        return evento("ReparoNaoLiberado", o, "motivo", motivo, "pecasFaltantes", faltantes);
    }

    public static EventoExecucao reparoIniciado(OrdemExecucao o) {
        return evento("ReparoIniciado", o,
                "mecanicoId", o.mecanicoReparo(),
                "iniciadoEm", o.reparoIniciadoEm().toString());
    }

    public static EventoExecucao reparoFinalizado(OrdemExecucao o) {
        return evento("ReparoFinalizado", o, "finalizadoEm", o.finalizadaEm().toString());
    }

    /** Confirmação da compensação — também quando a ordem nunca existiu (contrato §3). */
    public static EventoExecucao ordemExecucaoCancelada(String osId, int reservasLiberadas) {
        return new EventoExecucao("OrdemExecucaoCancelada",
                mapa("osId", osId, "reservasLiberadas", reservasLiberadas));
    }

    private static EventoExecucao evento(String tipo, OrdemExecucao o, Object... campos) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("osId", o.osId().valor().toString());
        payload.putAll(mapa(campos));
        return new EventoExecucao(tipo, payload);
    }

    public static Map<String, Object> mapa(Object... chaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chaveValor.length; i += 2) {
            m.put((String) chaveValor[i], chaveValor[i + 1]);
        }
        return m;
    }
}
