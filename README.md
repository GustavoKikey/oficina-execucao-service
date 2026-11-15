# Oficina — Execução Service

Microsserviço de **execução e produção** da oficina mecânica (Tech Challenge FIAP SOAT,
**fase 4**). Gerencia a **fila de execução**, o **diagnóstico** (itens de serviço e
peça), o **reparo**, o **catálogo de serviços** e o **estoque de peças** (reservas e
baixas). Comunica a finalização ao OS Service. É participante da saga orquestrada pelo
OS Service.

> **Situação: domínio e participação na saga implementados (etapa 3 de 5).** Deploy no
> EKS e README final com diagramas e evidências de cobertura entram na etapa 5.

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

## Mensageria

| Direção | Exchange / fila | Conteúdo |
| --- | --- | --- |
| Consome | `execucao.comandos` ← `oficina.comandos` (`rk: execucao`) | `AbrirOrdemExecucao`, `LiberarParaReparo`, `CancelarOrdemExecucao` |
| Publica | `oficina.eventos` (`rk: execucao.*`) | `DiagnosticoConcluido`, `ReparoLiberado`, `ReparoFinalizado`, … |
| DLQ | `execucao.comandos.dlq` | Mensagens rejeitadas após as retentativas |

## CI/CD

[`ci.yml`](.github/workflows/ci.yml) em todo push fora da `main` e em PRs: build,
testes, gate de cobertura, SonarCloud, imagem Docker e kubeconform. Deploy no EKS
(`homolog` → `hom`, `main` → `prod`) na etapa 5. `main` protegida, merge só por PR.
