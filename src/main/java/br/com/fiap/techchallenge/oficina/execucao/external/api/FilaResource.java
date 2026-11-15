package br.com.fiap.techchallenge.oficina.execucao.external.api;

import br.com.fiap.techchallenge.oficina.execucao.producao.controllers.ProducaoController;
import br.com.fiap.techchallenge.oficina.execucao.producao.dtos.OrdemExecucaoResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/** Fila de execução — "gerenciar a fila de execução da OS" (enunciado da fase 4). */
@Path("/fila")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Execução", description = "Fila de execução, diagnóstico e reparo")
public class FilaResource {

    private final ProducaoController controller;

    public FilaResource(ProducaoController controller) {
        this.controller = controller;
    }

    @GET
    @RolesAllowed({"MECANICO", "ATENDENTE", "ADMINISTRADOR"})
    @Operation(summary = "Fila de execução por ordem de chegada",
            description = "etapa=DIAGNOSTICO (padrão): na fila e em diagnóstico; etapa=REPARO: liberadas e em reparo")
    public List<OrdemExecucaoResponse> fila(@QueryParam("etapa") String etapa) {
        return controller.fila(etapa);
    }

}
