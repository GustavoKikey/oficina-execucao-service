package br.com.fiap.techchallenge.oficina.execucao.external.persistence.ordemexecucao;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ordens_execucao")
public class OrdemExecucaoJpaEntity {

    @Id
    @Column(name = "os_id", nullable = false, updatable = false)
    public UUID osId;

    @Column(name = "status", nullable = false, length = 32)
    public String status;

    @Column(name = "veiculo_placa", nullable = false, length = 7)
    public String veiculoPlaca;

    @Column(name = "veiculo_marca", length = 80)
    public String veiculoMarca;

    @Column(name = "veiculo_modelo", length = 120)
    public String veiculoModelo;

    @Column(name = "veiculo_ano")
    public Integer veiculoAno;

    @Column(name = "relato_cliente", length = 500)
    public String relatoCliente;

    @Column(name = "orcamento_id")
    public UUID orcamentoId;

    @Column(name = "mecanico_diagnostico", length = 80)
    public String mecanicoDiagnostico;

    @Column(name = "mecanico_reparo", length = 80)
    public String mecanicoReparo;

    @Column(name = "motivo_cancelamento", length = 32)
    public String motivoCancelamento;

    @Column(name = "entrou_na_fila_em", nullable = false)
    public OffsetDateTime entrouNaFilaEm;

    @Column(name = "diagnostico_iniciado_em")
    public OffsetDateTime diagnosticoIniciadoEm;

    @Column(name = "diagnostico_concluido_em")
    public OffsetDateTime diagnosticoConcluidoEm;

    @Column(name = "liberada_para_reparo_em")
    public OffsetDateTime liberadaParaReparoEm;

    @Column(name = "reparo_iniciado_em")
    public OffsetDateTime reparoIniciadoEm;

    @Column(name = "finalizada_em")
    public OffsetDateTime finalizadaEm;

    @Column(name = "cancelada_em")
    public OffsetDateTime canceladaEm;

    @OneToMany(mappedBy = "ordem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("posicao")
    public List<ItemExecucaoJpaEntity> itens = new ArrayList<>();
}
