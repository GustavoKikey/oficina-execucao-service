package br.com.fiap.techchallenge.oficina.execucao.suporte;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways.ServicoGateway;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.Peca;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaId;
import br.com.fiap.techchallenge.oficina.execucao.estoque.gateways.PecaGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.EventoExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.StatusExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.OrdemExecucaoGateway;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.PublicadorEventosGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Gateways em memória para os testes de use case — persistência real fica nos ITs. */
public final class Fakes {

    public static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 15, 14, 0, 0, 0, ZoneOffset.UTC);

    private Fakes() {}

    public static final class Ordens implements OrdemExecucaoGateway {
        public final Map<OrdemServicoId, OrdemExecucao> dados = new LinkedHashMap<>();

        @Override public OrdemExecucao salvar(OrdemExecucao o) { dados.put(o.osId(), o); return o; }
        @Override public Optional<OrdemExecucao> buscar(OrdemServicoId id) { return Optional.ofNullable(dados.get(id)); }
        @Override public Optional<OrdemExecucao> buscarComLock(OrdemServicoId id) { return buscar(id); }
        @Override public List<OrdemExecucao> listarPorStatus(Set<StatusExecucao> status) {
            return dados.values().stream().filter(o -> status.contains(o.status()))
                    .sorted(Comparator.comparing(OrdemExecucao::entrouNaFilaEm)).toList();
        }
    }

    public static final class Pecas implements PecaGateway {
        public final Map<PecaId, Peca> dados = new LinkedHashMap<>();

        public Peca com(Peca p) { dados.put(p.id(), p); return p; }

        @Override public Peca salvar(Peca p) { dados.put(p.id(), p); return p; }
        @Override public Optional<Peca> buscarPorId(PecaId id) { return Optional.ofNullable(dados.get(id)); }
        @Override public Optional<Peca> buscarPorIdComLock(PecaId id) { return buscarPorId(id); }
        @Override public List<Peca> listar() { return List.copyOf(dados.values()); }
        @Override public void remover(PecaId id) { dados.remove(id); }
    }

    public static final class Servicos implements ServicoGateway {
        public final Map<ServicoId, Servico> dados = new LinkedHashMap<>();

        public Servico com(Servico s) { dados.put(s.id(), s); return s; }

        @Override public Servico salvar(Servico s) { dados.put(s.id(), s); return s; }
        @Override public Optional<Servico> buscarPorId(ServicoId id) { return Optional.ofNullable(dados.get(id)); }
        @Override public List<Servico> listar() { return List.copyOf(dados.values()); }
        @Override public void remover(ServicoId id) { dados.remove(id); }
    }

    /** Outbox em memória: guarda cada evento com correlação e causa. */
    public static final class Eventos implements PublicadorEventosGateway {
        public record Publicado(EventoExecucao evento, OrdemServicoId correlationId, UUID causationId) {}

        public final List<Publicado> publicados = new ArrayList<>();

        @Override
        public void publicar(List<EventoExecucao> eventos, OrdemServicoId correlationId, UUID causationId) {
            eventos.forEach(e -> publicados.add(new Publicado(e, correlationId, causationId)));
        }

        public List<String> tipos() {
            return publicados.stream().map(p -> p.evento().tipo()).toList();
        }

        public EventoExecucao ultimo() {
            return publicados.get(publicados.size() - 1).evento();
        }
    }

    /** Relógio que o teste avança — testa validade de reserva sem dormir. */
    public static final class Relogio extends Clock {
        private Instant agora = AGORA.toInstant();

        public void avancar(Duration d) { agora = agora.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }
}
