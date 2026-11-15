/**
 * <b>Execução Service</b> — fila de execução, diagnóstico, reparo, catálogo de serviços e estoque de peças. Participante da saga orquestrada pelo OS Service.
 *
 * <p>Clean Architecture, como nas fases anteriores — a dependência aponta sempre para
 * dentro:
 * <pre>
 *   external   (api · persistence · messaging · config · observabilidade)
 *      ↓                                          Frameworks &amp; Drivers
 *   controllers · presenters · gateways · dtos    Interface Adapters
 *      ↓
 *   usecases                                      Application
 *      ↓
 *   entities                                      Enterprise (agregados, VOs)
 * </pre>
 *
 * <p>Desenho: repositório {@code oficina-os-service}, pasta {@code docs/fase-4/}.
 */
package br.com.fiap.techchallenge.oficina.execucao;
