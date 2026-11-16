# Oficina — Execução Service

[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=GustavoKikey_oficina-execucao-service&metric=alert_status)](https://sonarcloud.io/summary/overall?id=GustavoKikey_oficina-execucao-service)
[![Cobertura](https://sonarcloud.io/api/project_badges/measure?project=GustavoKikey_oficina-execucao-service&metric=coverage)](https://sonarcloud.io/component_measures?id=GustavoKikey_oficina-execucao-service&metric=coverage)
[![CI](https://github.com/GustavoKikey/oficina-execucao-service/actions/workflows/ci.yml/badge.svg)](https://github.com/GustavoKikey/oficina-execucao-service/actions/workflows/ci.yml)
[![CD](https://github.com/GustavoKikey/oficina-execucao-service/actions/workflows/cd.yml/badge.svg)](https://github.com/GustavoKikey/oficina-execucao-service/actions/workflows/cd.yml)

Microsserviço de **execução e produção** da oficina mecânica (Tech Challenge FIAP SOAT,
**fase 4**). Gerencia a **fila de execução**, o **diagnóstico** (itens de serviço e
peça), o **reparo**, o **catálogo de serviços** e o **estoque de peças** (reservas e
baixas). Comunica a finalização ao OS Service. É participante da saga orquestrada pelo
OS Service.

> **Situação:** domínio, saga, testes (unitários, integração), CI com SonarCloud e implantação na AWS (Terraform próprio e deploy contínuo no EKS) implementados.

## O que este serviço faz

| Quem | Como | O quê |
| --- | --- | --- |
| Orquestrador (OS Service) | comando `AbrirOrdemExecucao` | OS entra na fila de diagnóstico |
| Mecânico | `POST /ordens-execucao/{osId}/diagnostico/iniciar` | Pega a OS da fila |
| Mecânico | `POST /ordens-execucao/{osId}/itens/servicos` · `/itens/pecas` | Registra serviços do catálogo e peças do estoque |
| Mecânico | `POST /ordens-execucao/{osId}/diagnostico/concluir` | **Reserva as peças** (com validade) e publica `DiagnosticoConcluido` |
| Orquestrador | comando `LiberarParaReparo` (pagamento confirmado) | Baixa as peças e põe a OS na fila de reparo — ou responde `ReparoNaoLiberado` |
| Mecânico | `POST /ordens-execucao/{osId}/reparo/iniciar` · `/reparo/finalizar` | Executa o reparo e **comunica a finalização** ao OS Service |
| Orquestrador | comando `CancelarOrdemExecucao` (compensação) | Libera as reservas e cancela |
| Todos | `GET /fila?etapa=DIAGNOSTICO\|REPARO` | Fila de execução por ordem de chegada |
| Administrador | `/servicos` · `/pecas` | Catálogo de serviços e estoque |

**Reserva com validade** (`RESERVA_VALIDADE`, padrão 48 h): a oficina não segura peça para
sempre. Se a reserva vence e outra OS usa a peça antes do pagamento, a liberação do reparo
falha com `ESTOQUE_INSUFICIENTE` e a saga **estorna o pagamento** — é o cenário de
compensação demonstrado no vídeo.

Os três comandos são **idempotentes pela chave de negócio**: repetidos, não repetem o efeito
mas respondem de novo, o que destrava a saga quando uma resposta se perde (ADR 010).

---

## Documentação da arquitetura

A arquitetura do sistema inteiro (divisão em serviços, saga, contratos de mensagens,
ADRs) está no repositório do orquestrador:
[oficina-os-service/docs/fase-4](https://github.com/GustavoKikey/oficina-os-service/tree/main/docs/fase-4).
Os motivos para este serviço ficar com o diagnóstico, o catálogo e o estoque estão no
[ADR 006](https://github.com/GustavoKikey/oficina-os-service/blob/main/docs/fase-4/adr/adr-006-divisao-em-microsservicos.md).

## Arquitetura do serviço

Clean Architecture, como na fase 3: cada módulo de negócio tem `entities` (regras), `usecases` (fluxos), `gateways` (portas), `controllers`, `presenters` e `dtos`; tudo o que toca framework, banco ou rede fica em `external`, e o domínio não conhece Quarkus, banco nem RabbitMQ.

| Módulo | Responsabilidade |
| --- | --- |
| `producao` | Ordem de execução: fila, diagnóstico, liberação do reparo, reparo, cancelamento (participante da saga) |
| `estoque` | Peças, reservas com validade e baixas (lock pessimista) |
| `catalogo` | Catálogo de serviços |
| `shared` | Tipos de domínio comuns |
| `external` | Adaptadores: REST (`api`), PostgreSQL (`persistence`), RabbitMQ com outbox (`messaging`), métricas |

## Stack

Java 21 · Quarkus 3.40 (LTS) · PostgreSQL 16 + Flyway · RabbitMQ (SmallRye Reactive
Messaging) · JWT RS256 (só validação) · OpenTelemetry + Micrometer + logs JSON (New
Relic) · JUnit 5, REST-Assured, Testcontainers · JaCoCo (gate de 80%) · SonarCloud ·
Docker · Kubernetes (EKS)

## Executar localmente

Pré-requisitos: Docker e JDK 21. O Maven vem pelo wrapper (`./mvnw`).

```bash
./mvnw quarkus:dev     # o Quarkus sobe PostgreSQL e RabbitMQ sozinho (Dev Services)
```

O sistema inteiro sobe pelo `docker-compose.yml` do
[oficina-os-service](https://github.com/GustavoKikey/oficina-os-service). Nele, este
serviço responde em http://localhost:8081/swagger.

## Testes

```bash
./mvnw verify
```

Testes unitários e testes que sobem a aplicação contra PostgreSQL e RabbitMQ reais
(Testcontainers). O build **falha se a cobertura do serviço ficar abaixo de 80%**.

## Qualidade e cobertura

| Onde | O quê |
| --- | --- |
| [SonarCloud](https://sonarcloud.io/summary/overall?id=GustavoKikey_oficina-execucao-service) | Quality gate da `main` e de cada PR (badges no topo, atualizados a cada análise). Em 2026-11-16: cobertura **94,7%**, 0 bugs, 0 vulnerabilidades, 0 code smells |
| [Resumo de cada execução do CI](https://github.com/GustavoKikey/oficina-execucao-service/actions/workflows/ci.yml) | Tabela de cobertura do JaCoCo (instruções, branches, linhas); relatório completo no artefato `relatorio-jacoco` |
| Build local | `./mvnw verify` falha abaixo de **80%**; relatório em `target/site/jacoco/index.html` |

## Mensageria

| Direção | Exchange / fila | Conteúdo |
| --- | --- | --- |
| Consome | `execucao.comandos` ← `oficina.comandos` (`rk: execucao`) | `AbrirOrdemExecucao`, `LiberarParaReparo`, `CancelarOrdemExecucao` |
| Publica | `oficina.eventos` (`rk: execucao.*`) | `DiagnosticoConcluido`, `ReparoLiberado`, `ReparoFinalizado`, … |
| DLQ | `execucao.comandos.dlq` | Mensagens rejeitadas após as retentativas |

## CI/CD

| Workflow | Quando | O quê |
| --- | --- | --- |
| [`ci.yml`](.github/workflows/ci.yml) | PR para a `main` e push na `main` | Build, testes, gate de cobertura, SonarCloud (o plano gratuito analisa só PRs e a `main`), imagem Docker, kubeconform |
| [`cd.yml`](.github/workflows/cd.yml) | CI **verde** num commit da `main` | Imagem no ECR e [`scripts/implantar.sh`](scripts/implantar.sh) no EKS (`hom`); `prod` por *Run workflow*. Com a sessão do Learner Lab fechada, pula com aviso |
| [`infra.yml`](.github/workflows/infra.yml) | PR que mexe em `infra/`; aplicar sob comando | fmt, validate e tflint; apply/destroy no *Run workflow* |

`main` protegida: merge só por Pull Request, com as três checagens do CI obrigatórias. O
`ci.yml` e o `cd.yml` são idênticos nos três serviços.

## Implantação na AWS

**Infraestrutura própria** ([`infra/`](infra/), Terraform): RDS PostgreSQL `oficina-{env}-execucao`. Publica no SSM
(`/oficina/{env}/...`): `execucao/db/jdbc-url`, `execucao/db/secret-arn`. Depende só da plataforma
([oficina-infra-k8s](https://github.com/GustavoKikey/oficina-infra-k8s)), lida também pelo SSM.

**Deploy** ([`scripts/implantar.sh`](scripts/implantar.sh), chamado pelo `cd.yml` ou à mão):
cria os Secrets, aplica os manifestos de [`k8s/`](k8s/) com a imagem nova (os pods só
reiniciam quando a imagem ou a configuração mudam), espera o rollout e faz o smoke test.


| Secret no cluster | Origem | Para quê |
| --- | --- | --- |
| `oficina-execucao-db` | Secrets Manager (`execucao/db/secret-arn`) | usuário e senha do banco |
| `oficina-jwt-publica` | SSM (`jwt/public-key`, do OS Service) | só a chave pública: este serviço valida, não emite |
| `oficina-execucao-rabbitmq` | plataforma (`oficina-infra-k8s`) | usuário do RabbitMQ |
| `oficina-newrelic` | secret `NEW_RELIC_LICENSE_KEY` do repositório | opcional: liga a telemetria |

Exposição: Service `LoadBalancer` público, que **nasce fechado** e é aberto para o IP de quem
vai usar (`scripts/liberar-meu-ip.sh` do oficina-infra-k8s); o Swagger fica em `/swagger`.

O ambiente inteiro (plataforma, bancos, deploy dos três serviços e Lambda) sobe com
`scripts/aplicar.sh hom` e cai com `scripts/destruir.sh hom`, no oficina-infra-k8s.
Decisões e custo: [ADR 012](https://github.com/GustavoKikey/oficina-os-service/blob/main/docs/fase-4/adr/adr-012-implantacao-na-aws.md).
