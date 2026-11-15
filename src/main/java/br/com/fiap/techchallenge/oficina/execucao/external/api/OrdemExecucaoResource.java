package br.com.fiap.techchallenge.oficina.execucao.external.api;

import br.com.fiap.techchallenge.oficina.execucao.producao.controllers.ProducaoController;
import br.com.fiap.techchallenge.oficina.execucao.producao.dtos.AdicionarItemRequest;
import br.com.fiap.techchallenge.oficina.execucao.producao.dtos.OrdemExecucaoResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * O trabalho do mecânico: fila, diagnóstico (itens e reserva de peças) e reparo. As OS
 * chegam aqui pelo comando {@code AbrirOrdemExecucao} da saga, não por esta API.
 */
@Path("/ordens-execucao")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"MECANICO", "ADMINISTRADOR"})
@Tag(name = "Execução", description = "Fila de execução, diagnóstico e reparo")
public class OrdemExecucaoResource {

    private final ProducaoController controller;
    private final JsonWebToken jwt;

    public OrdemExecucaoResource(ProducaoController controller, JsonWebToken jwt) {
        this.controller = controller;
        this.jwt = jwt;
    }

    @GET
    @Path("{osId}")
    @RolesAllowed({"MECANICO", "ATENDENTE", "ADMINISTRADOR"})
    @Operation(summary = "Ordem de execução da OS: itens, reservas e etapas")
    @APIResponse(responseCode = "404", description = "OS ainda não chegou à Execução")
    public OrdemExecucaoResponse buscar(@PathParam("osId") UUID osId) {
        return controller.buscar(osId);
    }

    @POST
    @Path("{osId}/diagnostico/iniciar")
    @Operation(summary = "Mecânico pega a OS da fila e inicia o diagnóstico")
    @APIResponse(responseCode = "409", description = "OS não está na fila de diagnóstico")
    public OrdemExecucaoResponse iniciarDiagnostico(@PathParam("osId") UUID osId) {
        return controller.iniciarDiagnostico(osId, jwt.getName());
    }

    @POST
    @Path("{osId}/itens/servicos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra um serviço do catálogo no diagnóstico")
    public OrdemExecucaoResponse adicionarServico(@PathParam("osId") UUID osId, @Valid AdicionarItemRequest.Servico req) {
        return controller.adicionarServico(osId, req);
    }

    @POST
    @Path("{osId}/itens/pecas")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra uma peça no diagnóstico (confere o saldo, ainda sem reservar)")
    @APIResponse(responseCode = "422", description = "Estoque insuficiente")
    public OrdemExecucaoResponse adicionarPeca(@PathParam("osId") UUID osId, @Valid AdicionarItemRequest.Peca req) {
        return controller.adicionarPeca(osId, req);
    }

    @DELETE
    @Path("{osId}/itens/{itemId}")
    @Operation(summary = "Remove um item do diagnóstico")
    public OrdemExecucaoResponse removerItem(@PathParam("osId") UUID osId, @PathParam("itemId") UUID itemId) {
        return controller.removerItem(osId, itemId);
    }

    @POST
    @Path("{osId}/diagnostico/concluir")
    @Operation(summary = "Conclui o diagnóstico: reserva as peças e envia os itens para o orçamento")
    @APIResponse(responseCode = "422", description = "Estoque insuficiente — nada é reservado")
    @APIResponse(responseCode = "409", description = "Diagnóstico não iniciado ou sem itens")
    public OrdemExecucaoResponse concluirDiagnostico(@PathParam("osId") UUID osId) {
        return controller.concluirDiagnostico(osId);
    }

    @POST
    @Path("{osId}/reparo/iniciar")
    @Operation(summary = "Mecânico pega a OS liberada (paga) e inicia o reparo")
    @APIResponse(responseCode = "409", description = "OS não está na fila de reparo")
    public OrdemExecucaoResponse iniciarReparo(@PathParam("osId") UUID osId) {
        return controller.iniciarReparo(osId, jwt.getName());
    }

    @POST
    @Path("{osId}/reparo/finalizar")
    @Operation(summary = "Finaliza o reparo e comunica ao OS Service")
    @APIResponse(responseCode = "409", description = "OS não está em reparo")
    public OrdemExecucaoResponse finalizarReparo(@PathParam("osId") UUID osId) {
        return controller.finalizarReparo(osId);
    }
}
