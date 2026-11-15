package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.ReservaId;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz <b>Ordem de Execução</b>: a OS do ponto de vista da oficina — fila,
 * diagnóstico e reparo (enunciado da fase 4, "Execução e Produção").
 *
 * <p>Uma por OS, identificada pelo próprio {@code osId}: não existe id próprio, porque
 * o OS Service, a saga e os mecânicos falam da mesma OS. Os dados do veículo chegam no
 * comando {@code AbrirOrdemExecucao} (event-carried state) — a Execução nunca consulta
 * o OS Service.
 */
public class OrdemExecucao {

    private final OrdemServicoId osId;
    private final Veiculo veiculo;
    private final String relatoCliente;
    private StatusExecucao status;
    private final List<ItemExecucao> itens;
    private UUID orcamentoId;
    private String mecanicoDiagnostico;
    private String mecanicoReparo;
    private String motivoCancelamento;

    private final OffsetDateTime entrouNaFilaEm;
    private OffsetDateTime diagnosticoIniciadoEm;
    private OffsetDateTime diagnosticoConcluidoEm;
    private OffsetDateTime liberadaParaReparoEm;
    private OffsetDateTime reparoIniciadoEm;
    private OffsetDateTime finalizadaEm;
    private OffsetDateTime canceladaEm;

    /** Veículo como o atendimento registrou — só leitura aqui. */
    public record Veiculo(String placa, String marca, String modelo, Integer ano) {}

    private OrdemExecucao(OrdemServicoId osId, Veiculo veiculo, String relatoCliente, StatusExecucao status,
                          List<ItemExecucao> itens, OffsetDateTime entrouNaFilaEm) {
        this.osId = Objects.requireNonNull(osId, "osId");
        this.veiculo = Objects.requireNonNull(veiculo, "veiculo");
        this.relatoCliente = relatoCliente;
        this.status = Objects.requireNonNull(status, "status");
        this.itens = new ArrayList<>(itens);
        this.entrouNaFilaEm = Objects.requireNonNull(entrouNaFilaEm, "entrouNaFilaEm");
    }

    public static OrdemExecucao abrir(OrdemServicoId osId, Veiculo veiculo, String relatoCliente,
                                      OffsetDateTime agora) {
        return new OrdemExecucao(osId, veiculo, relatoCliente, StatusExecucao.NA_FILA_DIAGNOSTICO, List.of(), agora);
    }

    // ---------- Diagnóstico ----------

    public void iniciarDiagnostico(String mecanico, OffsetDateTime agora) {
        exigir(StatusExecucao.NA_FILA_DIAGNOSTICO, "iniciar o diagnóstico");
        this.mecanicoDiagnostico = mecanico;
        this.diagnosticoIniciadoEm = agora;
        this.status = StatusExecucao.EM_DIAGNOSTICO;
    }

    public ItemExecucao adicionarServico(UUID servicoId, String descricao, Dinheiro valor) {
        exigir(StatusExecucao.EM_DIAGNOSTICO, "registrar itens");
        ItemExecucao item = ItemExecucao.novo(ItemExecucao.Tipo.SERVICO, servicoId, descricao, 1, valor);
        itens.add(item);
        return item;
    }

    public ItemExecucao adicionarPeca(UUID pecaId, String descricao, int quantidade, Dinheiro valorUnitario) {
        exigir(StatusExecucao.EM_DIAGNOSTICO, "registrar itens");
        ItemExecucao item = ItemExecucao.novo(ItemExecucao.Tipo.PECA, pecaId, descricao, quantidade, valorUnitario);
        itens.add(item);
        return item;
    }

    public void removerItem(UUID itemId) {
        exigir(StatusExecucao.EM_DIAGNOSTICO, "remover itens");
        if (!itens.removeIf(i -> i.id().equals(itemId))) {
            throw new ItemExecucaoNaoEncontradoException(itemId);
        }
    }

    /**
     * Fecha o diagnóstico. Quem reserva as peças é o use case (outro agregado, mesma
     * transação); aqui a ordem só registra o resultado.
     */
    public void concluirDiagnostico(OffsetDateTime agora) {
        exigir(StatusExecucao.EM_DIAGNOSTICO, "concluir o diagnóstico");
        if (itens.isEmpty()) {
            throw new TransicaoExecucaoInvalidaException(
                    "Diagnóstico sem itens: registre ao menos um serviço ou peça antes de concluir");
        }
        boolean pecaSemReserva = itens.stream()
                .anyMatch(i -> i.tipo() == ItemExecucao.Tipo.PECA && i.reservaId() == null);
        if (pecaSemReserva) {
            throw new IllegalStateException("Toda peça precisa estar reservada para concluir o diagnóstico");
        }
        this.diagnosticoConcluidoEm = agora;
        this.status = StatusExecucao.AGUARDANDO_LIBERACAO;
    }

    public void registrarReserva(UUID itemId, ReservaId reserva) {
        itens.stream().filter(i -> i.id().equals(itemId)).findFirst()
                .orElseThrow(() -> new ItemExecucaoNaoEncontradoException(itemId))
                .registrarReserva(reserva);
    }

    // ---------- Reparo ----------

    public void liberarParaReparo(UUID orcamento, OffsetDateTime agora) {
        exigir(StatusExecucao.AGUARDANDO_LIBERACAO, "liberar para reparo");
        this.orcamentoId = orcamento;
        this.liberadaParaReparoEm = agora;
        this.status = StatusExecucao.NA_FILA_REPARO;
    }

    public void iniciarReparo(String mecanico, OffsetDateTime agora) {
        exigir(StatusExecucao.NA_FILA_REPARO, "iniciar o reparo");
        this.mecanicoReparo = mecanico;
        this.reparoIniciadoEm = agora;
        this.status = StatusExecucao.EM_REPARO;
    }

    public void finalizarReparo(OffsetDateTime agora) {
        exigir(StatusExecucao.EM_REPARO, "finalizar o reparo");
        this.finalizadaEm = agora;
        this.status = StatusExecucao.FINALIZADA;
    }

    // ---------- Compensação ----------

    /** Já cancelada: nada a fazer (idempotente). Finalizada não volta atrás. */
    public void cancelar(String motivo, OffsetDateTime agora) {
        if (status == StatusExecucao.CANCELADA) {
            return;
        }
        if (status == StatusExecucao.FINALIZADA) {
            throw new TransicaoExecucaoInvalidaException("OS " + osId + " já foi finalizada e não pode ser cancelada");
        }
        this.motivoCancelamento = motivo;
        this.canceladaEm = agora;
        this.status = StatusExecucao.CANCELADA;
    }

    private void exigir(StatusExecucao esperado, String acao) {
        if (status != esperado) {
            throw new TransicaoExecucaoInvalidaException(
                    "Não é possível " + acao + " com a OS " + osId + " em " + status + " (esperado " + esperado + ")");
        }
    }

    public List<ItemExecucao> itensDePeca() {
        return itens.stream().filter(i -> i.tipo() == ItemExecucao.Tipo.PECA).toList();
    }

    // ---------- Reconstituição e acessores ----------

    public static OrdemExecucao reconstituir(Dados d) {
        OrdemExecucao o = new OrdemExecucao(d.osId, d.veiculo, d.relatoCliente, d.status, d.itens, d.entrouNaFilaEm);
        o.orcamentoId = d.orcamentoId;
        o.mecanicoDiagnostico = d.mecanicoDiagnostico;
        o.mecanicoReparo = d.mecanicoReparo;
        o.motivoCancelamento = d.motivoCancelamento;
        o.diagnosticoIniciadoEm = d.diagnosticoIniciadoEm;
        o.diagnosticoConcluidoEm = d.diagnosticoConcluidoEm;
        o.liberadaParaReparoEm = d.liberadaParaReparoEm;
        o.reparoIniciadoEm = d.reparoIniciadoEm;
        o.finalizadaEm = d.finalizadaEm;
        o.canceladaEm = d.canceladaEm;
        return o;
    }

    public OrdemServicoId osId()                  { return osId; }
    public Veiculo veiculo()                      { return veiculo; }
    public String relatoCliente()                 { return relatoCliente; }
    public StatusExecucao status()                { return status; }
    public List<ItemExecucao> itens()             { return Collections.unmodifiableList(itens); }
    public UUID orcamentoId()                     { return orcamentoId; }
    public String mecanicoDiagnostico()           { return mecanicoDiagnostico; }
    public String mecanicoReparo()                { return mecanicoReparo; }
    public String motivoCancelamento()            { return motivoCancelamento; }
    public OffsetDateTime entrouNaFilaEm()        { return entrouNaFilaEm; }
    public OffsetDateTime diagnosticoIniciadoEm() { return diagnosticoIniciadoEm; }
    public OffsetDateTime diagnosticoConcluidoEm() { return diagnosticoConcluidoEm; }
    public OffsetDateTime liberadaParaReparoEm()  { return liberadaParaReparoEm; }
    public OffsetDateTime reparoIniciadoEm()      { return reparoIniciadoEm; }
    public OffsetDateTime finalizadaEm()          { return finalizadaEm; }
    public OffsetDateTime canceladaEm()           { return canceladaEm; }

    public Dinheiro total() {
        Dinheiro total = Dinheiro.ZERO;
        for (ItemExecucao i : itens) {
            total = total.somar(i.subtotal());
        }
        return total;
    }

    /** Parâmetros de reconstituição. */
    public static final class Dados {
        public OrdemServicoId osId;
        public Veiculo veiculo;
        public String relatoCliente;
        public StatusExecucao status;
        public List<ItemExecucao> itens = List.of();
        public UUID orcamentoId;
        public String mecanicoDiagnostico;
        public String mecanicoReparo;
        public String motivoCancelamento;
        public OffsetDateTime entrouNaFilaEm;
        public OffsetDateTime diagnosticoIniciadoEm;
        public OffsetDateTime diagnosticoConcluidoEm;
        public OffsetDateTime liberadaParaReparoEm;
        public OffsetDateTime reparoIniciadoEm;
        public OffsetDateTime finalizadaEm;
        public OffsetDateTime canceladaEm;
    }
}
