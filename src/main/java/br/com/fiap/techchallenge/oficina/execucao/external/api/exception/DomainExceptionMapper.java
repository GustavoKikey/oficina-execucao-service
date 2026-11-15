package br.com.fiap.techchallenge.oficina.execucao.external.api.exception;

import br.com.fiap.techchallenge.oficina.execucao.catalogo.entities.ServicoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.EstoqueInsuficienteException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.PecaPossuiReservasAtivasException;
import br.com.fiap.techchallenge.oficina.execucao.estoque.entities.ReservaInvalidaException;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.ItemExecucaoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.OrdemExecucaoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.execucao.producao.entities.TransicaoExecucaoInvalidaException;
import br.com.fiap.techchallenge.oficina.execucao.shared.entities.DomainException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduz exceções de domínio em respostas HTTP com corpo {@link ApiError}:
 * 404 (não encontrado), 409 (estado incompatível), 422 (sem estoque) e 400 (o resto).
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(DomainException ex) {
        int status = mapearStatus(ex);
        return Response.status(status).entity(ApiError.of(status, reasonPhrase(status), ex.getMessage())).build();
    }

    static int mapearStatus(DomainException ex) {
        if (ex instanceof ServicoNaoEncontradoException
                || ex instanceof PecaNaoEncontradaException
                || ex instanceof OrdemExecucaoNaoEncontradaException
                || ex instanceof ItemExecucaoNaoEncontradoException) {
            return Response.Status.NOT_FOUND.getStatusCode();
        }
        if (ex instanceof TransicaoExecucaoInvalidaException
                || ex instanceof PecaPossuiReservasAtivasException
                || ex instanceof ReservaInvalidaException) {
            return Response.Status.CONFLICT.getStatusCode();
        }
        if (ex instanceof EstoqueInsuficienteException) {
            return UNPROCESSABLE_ENTITY;
        }
        return Response.Status.BAD_REQUEST.getStatusCode();
    }

    private static String reasonPhrase(int status) {
        Response.Status s = Response.Status.fromStatusCode(status);
        return s != null ? s.getReasonPhrase() : "Unprocessable Entity";
    }
}
