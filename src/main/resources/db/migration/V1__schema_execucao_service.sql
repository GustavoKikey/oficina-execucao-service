-- =====================================================================
-- Execução Service — schema inicial (fase 4)
-- ---------------------------------------------------------------------
-- Banco PRÓPRIO da Execução: catálogo de serviços e estoque (vindos do
-- monolito da fase 3) e a ordem de execução (fila, diagnóstico, reparo).
-- os_id referencia a OS do OS Service só por UUID — sem FK entre bancos.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Catálogo de serviços (mão de obra)
-- ---------------------------------------------------------------------
CREATE TABLE servicos (
    id          UUID            PRIMARY KEY,
    descricao   VARCHAR(200)    NOT NULL,
    valor_base  NUMERIC(12, 2)  NOT NULL CHECK (valor_base >= 0),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Estoque
-- ---------------------------------------------------------------------
CREATE TABLE pecas (
    id                UUID            PRIMARY KEY,
    descricao         VARCHAR(200)    NOT NULL,
    valor_unitario    NUMERIC(12, 2)  NOT NULL CHECK (valor_unitario >= 0),
    quantidade_total  INTEGER         NOT NULL CHECK (quantidade_total >= 0),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE reservas (
    id                UUID         PRIMARY KEY,
    peca_id           UUID         NOT NULL REFERENCES pecas(id) ON DELETE CASCADE,
    ordem_servico_id  UUID         NOT NULL,
    quantidade        INTEGER      NOT NULL CHECK (quantidade > 0),
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('ATIVA', 'BAIXADA', 'CANCELADA')),
    criada_em         TIMESTAMPTZ  NOT NULL,
    -- Fase 4: a reserva vale até aqui; depois deixa de segurar saldo (expiração preguiçosa).
    valida_ate        TIMESTAMPTZ
);

CREATE INDEX idx_reservas_peca_id ON reservas (peca_id);
CREATE INDEX idx_reservas_os_id   ON reservas (ordem_servico_id);

-- ---------------------------------------------------------------------
-- Ordem de execução (uma por OS)
-- ---------------------------------------------------------------------
CREATE TABLE ordens_execucao (
    os_id                     UUID          PRIMARY KEY,
    status                    VARCHAR(32)   NOT NULL CHECK (status IN
        ('NA_FILA_DIAGNOSTICO','EM_DIAGNOSTICO','AGUARDANDO_LIBERACAO','NA_FILA_REPARO',
         'EM_REPARO','FINALIZADA','CANCELADA')),
    -- Veículo como o atendimento registrou (chega no comando AbrirOrdemExecucao).
    veiculo_placa             VARCHAR(7)    NOT NULL,
    veiculo_marca             VARCHAR(80),
    veiculo_modelo            VARCHAR(120),
    veiculo_ano               INTEGER,
    relato_cliente            VARCHAR(500),
    orcamento_id              UUID,
    mecanico_diagnostico      VARCHAR(80),
    mecanico_reparo           VARCHAR(80),
    motivo_cancelamento       VARCHAR(32),
    entrou_na_fila_em         TIMESTAMPTZ   NOT NULL,
    diagnostico_iniciado_em   TIMESTAMPTZ,
    diagnostico_concluido_em  TIMESTAMPTZ,
    liberada_para_reparo_em   TIMESTAMPTZ,
    reparo_iniciado_em        TIMESTAMPTZ,
    finalizada_em             TIMESTAMPTZ,
    cancelada_em              TIMESTAMPTZ
);

-- A fila é lida por status, em ordem de chegada.
CREATE INDEX idx_ordens_execucao_fila ON ordens_execucao (status, entrou_na_fila_em);

CREATE TABLE ordem_execucao_itens (
    id              UUID            PRIMARY KEY,
    os_id           UUID            NOT NULL REFERENCES ordens_execucao(os_id) ON DELETE CASCADE,
    posicao         INTEGER         NOT NULL,
    tipo            VARCHAR(8)      NOT NULL CHECK (tipo IN ('SERVICO','PECA')),
    referencia_id   UUID            NOT NULL,
    descricao       VARCHAR(200)    NOT NULL,
    quantidade      INTEGER         NOT NULL CHECK (quantidade > 0),
    valor_unitario  NUMERIC(12, 2)  NOT NULL CHECK (valor_unitario >= 0),
    reserva_id      UUID
);

CREATE INDEX idx_ordem_execucao_itens_os ON ordem_execucao_itens (os_id);

-- ---------------------------------------------------------------------
-- Outbox (ADR 010) — eventos publicados pelo relay depois do commit
-- ---------------------------------------------------------------------
CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    message_id      UUID         NOT NULL UNIQUE,
    tipo            VARCHAR(64)  NOT NULL,
    routing_key     VARCHAR(64)  NOT NULL,
    correlation_id  UUID         NOT NULL,
    envelope        TEXT         NOT NULL,
    headers         TEXT         NOT NULL DEFAULT '{}',
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_em    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pendentes ON outbox (id) WHERE publicado_em IS NULL;
