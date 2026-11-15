package br.com.fiap.techchallenge.oficina.execucao.producao.entities;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.ReservaId;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;

import java.util.Objects;
import java.util.UUID;

/**
 * Item registrado pelo mecânico no diagnóstico: um serviço do catálogo ou uma peça do
 * estoque, com o preço do momento (o catálogo pode mudar depois; o orçamento, não).
 */
public class ItemExecucao {

    public enum Tipo { SERVICO, PECA }

    private final UUID id;
    private final Tipo tipo;
    private final UUID referenciaId;
    private final String descricao;
    private final int quantidade;
    private final Dinheiro valorUnitario;
    private ReservaId reservaId;

    private ItemExecucao(UUID id, Tipo tipo, UUID referenciaId, String descricao, int quantidade,
                         Dinheiro valorUnitario, ReservaId reservaId) {
        this.id = Objects.requireNonNull(id, "id");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.referenciaId = Objects.requireNonNull(referenciaId, "referenciaId");
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Descrição do item é obrigatória");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva: " + quantidade);
        }
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.valorUnitario = Objects.requireNonNull(valorUnitario, "valorUnitario");
        this.reservaId = reservaId;
    }

    static ItemExecucao novo(Tipo tipo, UUID referenciaId, String descricao, int quantidade, Dinheiro valor) {
        return new ItemExecucao(UUID.randomUUID(), tipo, referenciaId, descricao, quantidade, valor, null);
    }

    public static ItemExecucao reconstituir(UUID id, Tipo tipo, UUID referenciaId, String descricao,
                                            int quantidade, Dinheiro valorUnitario, ReservaId reservaId) {
        return new ItemExecucao(id, tipo, referenciaId, descricao, quantidade, valorUnitario, reservaId);
    }

    void registrarReserva(ReservaId reserva) {
        if (tipo != Tipo.PECA) {
            throw new IllegalStateException("Só item de peça tem reserva");
        }
        this.reservaId = Objects.requireNonNull(reserva, "reserva");
    }

    public Dinheiro subtotal() {
        return valorUnitario.multiplicar(quantidade);
    }

    public UUID id()                { return id; }
    public Tipo tipo()              { return tipo; }
    public UUID referenciaId()      { return referenciaId; }
    public String descricao()       { return descricao; }
    public int quantidade()         { return quantidade; }
    public Dinheiro valorUnitario() { return valorUnitario; }
    public ReservaId reservaId()    { return reservaId; }
}
