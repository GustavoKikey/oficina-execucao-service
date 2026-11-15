package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

/**
 * Etapas de uma OS dentro da oficina (Execução Service).
 * <pre>
 *   (AbrirOrdemExecucao)  → NA_FILA_DIAGNOSTICO
 *   NA_FILA_DIAGNOSTICO   → EM_DIAGNOSTICO        (mecânico inicia)
 *   EM_DIAGNOSTICO        → AGUARDANDO_LIBERACAO  (mecânico conclui; peças reservadas)
 *   AGUARDANDO_LIBERACAO  → NA_FILA_REPARO        (LiberarParaReparo: pagamento confirmado)
 *   NA_FILA_REPARO        → EM_REPARO             (mecânico inicia)
 *   EM_REPARO             → FINALIZADA            (mecânico finaliza)
 *   qualquer, exceto FINALIZADA → CANCELADA       (CancelarOrdemExecucao — compensação)
 * </pre>
 */
public enum StatusExecucao {
    NA_FILA_DIAGNOSTICO(Etapa.DIAGNOSTICO),
    EM_DIAGNOSTICO(Etapa.DIAGNOSTICO),
    AGUARDANDO_LIBERACAO(null),
    NA_FILA_REPARO(Etapa.REPARO),
    EM_REPARO(Etapa.REPARO),
    FINALIZADA(null),
    CANCELADA(null);

    /** As duas filas que o mecânico enxerga. */
    public enum Etapa { DIAGNOSTICO, REPARO }

    private final Etapa etapa;

    StatusExecucao(Etapa etapa) {
        this.etapa = etapa;
    }

    /** Fila em que a OS aparece; nulo quando não está em fila nenhuma. */
    public Etapa etapa() {
        return etapa;
    }
}
