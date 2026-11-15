package br.com.fiap.techchallenge.oficina.execucao.estoque.entities;

import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;
import org.junit.jupiter.api.Test;

import static br.com.fiap.techchallenge.oficina.execucao.suporte.Fakes.AGORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fase 4: a reserva tem validade e expira sem agendador (saga.md §4). */
class ValidadeReservaTest {

    @Test
    void reservaVencidaDevolveOSaldo() {
        Peca peca = Peca.novo("Pastilha", Dinheiro.de("95.00"), 3);
        Reserva r = peca.reservar(OrdemServicoId.novo(), 2, AGORA, AGORA.plusHours(48));

        assertEquals(1, peca.saldoDisponivel(AGORA.plusHours(47)));
        assertEquals(3, peca.saldoDisponivel(AGORA.plusHours(48)), "no instante da validade já não segura");
        assertTrue(r.expiradaEm(AGORA.plusHours(49)));
        assertFalse(r.ativaEm(AGORA.plusHours(49)));
    }

    @Test
    void reservaVencidaNaoPodeSerBaixadaMasPodeSerLiberada() {
        Peca peca = Peca.novo("Pastilha", Dinheiro.de("95.00"), 3);
        Reserva r = peca.reservar(OrdemServicoId.novo(), 2, AGORA, AGORA.plusHours(1));

        ReservaInvalidaException erro = assertThrows(ReservaInvalidaException.class,
                () -> peca.baixar(r.id(), AGORA.plusHours(2)));
        assertTrue(erro.getMessage().contains("expirou"));

        peca.cancelarReserva(r.id());
        assertEquals(StatusReserva.CANCELADA, peca.reserva(r.id()).status());
        assertFalse(peca.possuiReservasAtivas());
    }

    @Test
    void outraOsPodeUsarOSaldoDeReservaVencida() {
        Peca peca = Peca.novo("Pastilha", Dinheiro.de("95.00"), 2);
        peca.reservar(OrdemServicoId.novo(), 2, AGORA, AGORA.plusHours(1));

        assertThrows(EstoqueInsuficienteException.class,
                () -> peca.reservar(OrdemServicoId.novo(), 1, AGORA.plusMinutes(30), null));
        Reserva outra = peca.reservar(OrdemServicoId.novo(), 2, AGORA.plusHours(2), null);

        peca.baixar(outra.id(), AGORA.plusHours(3));
        assertEquals(0, peca.quantidadeTotal());
    }

    @Test
    void reservaSemValidadeNaoExpira() {
        Peca peca = Peca.novo("Filtro", Dinheiro.de("30.00"), 1);
        Reserva r = peca.reservar(OrdemServicoId.novo(), 1);

        assertTrue(r.ativaEm(AGORA.plusYears(10)));
        assertFalse(r.expiradaEm(AGORA.plusYears(10)));
        assertTrue(peca.possuiReservasAtivas());
    }
}
