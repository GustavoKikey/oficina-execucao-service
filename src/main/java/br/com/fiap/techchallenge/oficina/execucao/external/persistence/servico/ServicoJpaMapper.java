package br.com.fiap.techchallenge.oficina.execucao.external.persistence.servico;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.Servico;
import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoId;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.Dinheiro;

final class ServicoJpaMapper {

    private ServicoJpaMapper() {}

    static ServicoJpaEntity toEntity(Servico servico, ServicoJpaEntity existente) {
        ServicoJpaEntity entity = existente != null ? existente : new ServicoJpaEntity();
        entity.id = servico.id().valor();
        entity.descricao = servico.descricao();
        entity.valorBase = servico.valorBase().valor();
        return entity;
    }

    static Servico toDomain(ServicoJpaEntity entity) {
        return Servico.reconstituir(
                ServicoId.de(entity.id),
                entity.descricao,
                Dinheiro.de(entity.valorBase)
        );
    }
}
