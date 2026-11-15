package br.com.fiap.techchallenge.oficina.execucao.estoque.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Entidade interna do agregado {@link Peca}.
 *
 * <p>Não é raiz: criada e mutada apenas por {@link Peca}. Métodos que alteram estado
 * são package-private para impedir manipulação direta de fora do agregado.
 *
 * <p><b>Validade (fase 4).</b> A oficina não segura peça para sempre esperando o cliente
 * decidir: a reserva feita no fim do diagnóstico vale até {@code validaAte}. A expiração
 * é preguiçosa — não há agendador: passado o prazo, a reserva simplesmente deixa de
 * contar como ativa e o saldo volta a ficar disponível para outras OS (saga.md §4).
 */
public class Reserva {

    private final ReservaId id;
    private final OrdemServicoId ordemServicoId;
    private final int quantidade;
    private StatusReserva status;
    private final OffsetDateTime criadaEm;
    /** Nulo = sem validade (reservas anteriores à fase 4). */
    private final OffsetDateTime validaAte;

    private Reserva(ReservaId id, OrdemServicoId ordemServicoId, int quantidade,
                    StatusReserva status, OffsetDateTime criadaEm, OffsetDateTime validaAte) {
        this.id = Objects.requireNonNull(id, "id");
        this.ordemServicoId = Objects.requireNonNull(ordemServicoId, "ordemServicoId");
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade da reserva deve ser positiva: " + quantidade);
        }
        this.quantidade = quantidade;
        this.status = Objects.requireNonNull(status, "status");
        this.criadaEm = Objects.requireNonNull(criadaEm, "criadaEm");
        this.validaAte = validaAte;
    }

    static Reserva nova(OrdemServicoId ordemServicoId, int quantidade, OffsetDateTime agora,
                        OffsetDateTime validaAte) {
        return new Reserva(ReservaId.novo(), ordemServicoId, quantidade, StatusReserva.ATIVA, agora, validaAte);
    }

    public static Reserva reconstituir(ReservaId id, OrdemServicoId ordemServicoId, int quantidade,
                                       StatusReserva status, OffsetDateTime criadaEm, OffsetDateTime validaAte) {
        return new Reserva(id, ordemServicoId, quantidade, status, criadaEm, validaAte);
    }

    void marcarBaixada(OffsetDateTime agora) {
        if (!ativaEm(agora)) {
            throw new ReservaInvalidaException("Reserva " + id + " não pode ser baixada: "
                    + (status == StatusReserva.ATIVA ? "expirou em " + validaAte : "status atual " + status));
        }
        this.status = StatusReserva.BAIXADA;
    }

    /** Liberar uma reserva vencida também é válido: é a compensação limpando o que sobrou. */
    void cancelar() {
        if (status != StatusReserva.ATIVA) {
            throw new ReservaInvalidaException(
                    "Reserva " + id + " não pode ser cancelada: status atual " + status);
        }
        this.status = StatusReserva.CANCELADA;
    }

    public ReservaId id()                   { return id; }
    public OrdemServicoId ordemServicoId()  { return ordemServicoId; }
    public int quantidade()                 { return quantidade; }
    public StatusReserva status()           { return status; }
    public OffsetDateTime criadaEm()        { return criadaEm; }
    public OffsetDateTime validaAte()       { return validaAte; }

    /** Ativa e dentro da validade: é o que segura saldo. */
    public boolean ativaEm(OffsetDateTime agora) {
        return status == StatusReserva.ATIVA && (validaAte == null || validaAte.isAfter(agora));
    }

    public boolean ativa() {
        return ativaEm(OffsetDateTime.now());
    }

    public boolean expiradaEm(OffsetDateTime agora) {
        return status == StatusReserva.ATIVA && validaAte != null && !validaAte.isAfter(agora);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reserva that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
