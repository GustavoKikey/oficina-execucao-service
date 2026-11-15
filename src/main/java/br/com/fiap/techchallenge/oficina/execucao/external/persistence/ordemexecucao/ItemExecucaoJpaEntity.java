package br.com.fiap.techchallenge.oficina.execucao.external.persistence.ordemexecucao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ordem_execucao_itens")
public class ItemExecucaoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne
    @JoinColumn(name = "os_id", nullable = false)
    public OrdemExecucaoJpaEntity ordem;

    @Column(name = "posicao", nullable = false)
    public int posicao;

    @Column(name = "tipo", nullable = false, length = 8)
    public String tipo;

    @Column(name = "referencia_id", nullable = false)
    public UUID referenciaId;

    @Column(name = "descricao", nullable = false, length = 200)
    public String descricao;

    @Column(name = "quantidade", nullable = false)
    public int quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    public BigDecimal valorUnitario;

    @Column(name = "reserva_id")
    public UUID reservaId;
}
