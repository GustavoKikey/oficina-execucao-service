package br.com.fiap.techchallenge.oficina.execucao.producao.gateways;

import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucao;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.StatusExecucao;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.OrdemServicoId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrdemExecucaoGateway {

    OrdemExecucao salvar(OrdemExecucao ordem);

    Optional<OrdemExecucao> buscar(OrdemServicoId osId);

    /** Trava a linha até o fim da transação: comando e mecânico não se atropelam. */
    Optional<OrdemExecucao> buscarComLock(OrdemServicoId osId);

    /** Ordens nos status pedidos, por ordem de chegada na fila (FIFO). */
    List<OrdemExecucao> listarPorStatus(Set<StatusExecucao> status);
}
