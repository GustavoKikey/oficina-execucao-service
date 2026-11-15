package br.com.fiap.techchallenge.oficina.execucao.external.persistence.ordemexecucao;

import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.ReservaId;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.ItemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.StatusExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.gateways.OrdemExecucaoGateway;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class OrdemExecucaoGatewayImpl implements OrdemExecucaoGateway,
        PanacheRepositoryBase<OrdemExecucaoJpaEntity, UUID> {

    @Override
    public OrdemExecucao salvar(OrdemExecucao o) {
        OrdemExecucaoJpaEntity e = findById(o.osId().valor());
        boolean nova = e == null;
        if (nova) {
            e = new OrdemExecucaoJpaEntity();
            e.osId = o.osId().valor();
        }
        e.status = o.status().name();
        e.veiculoPlaca = o.veiculo().placa();
        e.veiculoMarca = o.veiculo().marca();
        e.veiculoModelo = o.veiculo().modelo();
        e.veiculoAno = o.veiculo().ano();
        e.relatoCliente = o.relatoCliente();
        e.orcamentoId = o.orcamentoId();
        e.mecanicoDiagnostico = o.mecanicoDiagnostico();
        e.mecanicoReparo = o.mecanicoReparo();
        e.motivoCancelamento = o.motivoCancelamento();
        e.entrouNaFilaEm = o.entrouNaFilaEm();
        e.diagnosticoIniciadoEm = o.diagnosticoIniciadoEm();
        e.diagnosticoConcluidoEm = o.diagnosticoConcluidoEm();
        e.liberadaParaReparoEm = o.liberadaParaReparoEm();
        e.reparoIniciadoEm = o.reparoIniciadoEm();
        e.finalizadaEm = o.finalizadaEm();
        e.canceladaEm = o.canceladaEm();
        sincronizarItens(o, e);
        if (nova) {
            persist(e);
        }
        return o;
    }

    private static void sincronizarItens(OrdemExecucao o, OrdemExecucaoJpaEntity e) {
        Map<UUID, ItemExecucaoJpaEntity> existentes = new HashMap<>();
        e.itens.forEach(i -> existentes.put(i.id, i));
        e.itens.clear();
        int posicao = 0;
        for (ItemExecucao item : o.itens()) {
            ItemExecucaoJpaEntity i = existentes.get(item.id());
            if (i == null) {
                i = new ItemExecucaoJpaEntity();
                i.id = item.id();
                i.ordem = e;
                i.tipo = item.tipo().name();
                i.referenciaId = item.referenciaId();
                i.descricao = item.descricao();
                i.quantidade = item.quantidade();
                i.valorUnitario = item.valorUnitario().valor();
            }
            i.posicao = posicao++;
            i.reservaId = item.reservaId() == null ? null : item.reservaId().valor();
            e.itens.add(i);
        }
    }

    @Override
    public Optional<OrdemExecucao> buscar(OrdemServicoId osId) {
        return findByIdOptional(osId.valor()).map(OrdemExecucaoGatewayImpl::toDomain);
    }

    @Override
    public Optional<OrdemExecucao> buscarComLock(OrdemServicoId osId) {
        return Optional.ofNullable(findById(osId.valor(), LockModeType.PESSIMISTIC_WRITE))
                .map(OrdemExecucaoGatewayImpl::toDomain);
    }

    @Override
    public List<OrdemExecucao> listarPorStatus(Set<StatusExecucao> status) {
        if (status.isEmpty()) {
            return List.of();
        }
        return list("status in ?1", Sort.ascending("entrouNaFilaEm"), status.stream().map(Enum::name).toList())
                .stream().map(OrdemExecucaoGatewayImpl::toDomain).toList();
    }

    static OrdemExecucao toDomain(OrdemExecucaoJpaEntity e) {
        OrdemExecucao.Dados d = new OrdemExecucao.Dados();
        d.osId = OrdemServicoId.de(e.osId);
        d.veiculo = new OrdemExecucao.Veiculo(e.veiculoPlaca, e.veiculoMarca, e.veiculoModelo, e.veiculoAno);
        d.relatoCliente = e.relatoCliente;
        d.status = StatusExecucao.valueOf(e.status);
        d.itens = e.itens.stream().map(i -> ItemExecucao.reconstituir(i.id, ItemExecucao.Tipo.valueOf(i.tipo),
                i.referenciaId, i.descricao, i.quantidade, Dinheiro.de(i.valorUnitario),
                i.reservaId == null ? null : ReservaId.de(i.reservaId))).toList();
        d.orcamentoId = e.orcamentoId;
        d.mecanicoDiagnostico = e.mecanicoDiagnostico;
        d.mecanicoReparo = e.mecanicoReparo;
        d.motivoCancelamento = e.motivoCancelamento;
        d.entrouNaFilaEm = e.entrouNaFilaEm;
        d.diagnosticoIniciadoEm = e.diagnosticoIniciadoEm;
        d.diagnosticoConcluidoEm = e.diagnosticoConcluidoEm;
        d.liberadaParaReparoEm = e.liberadaParaReparoEm;
        d.reparoIniciadoEm = e.reparoIniciadoEm;
        d.finalizadaEm = e.finalizadaEm;
        d.canceladaEm = e.canceladaEm;
        return OrdemExecucao.reconstituir(d);
    }
}
