package br.com.fiap.techchallenge.oficina.execucao.catalogo.gateways;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;

import java.util.List;
import java.util.Optional;

public interface ServicoGateway {

    Servico salvar(Servico servico);

    Optional<Servico> buscarPorId(ServicoId id);

    List<Servico> listar();

    void remover(ServicoId id);
}
