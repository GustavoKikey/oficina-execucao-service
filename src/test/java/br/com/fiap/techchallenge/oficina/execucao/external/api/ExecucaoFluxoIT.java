package br.com.fiap.techchallenge.oficina.execucao.external.api;

import br.com.fiap.techchallenge.oficina.execucao.suporte.MensageriaEmMemoria;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Execução de ponta a ponta: comandos da saga pela mensageria (conector em memória),
 * trabalho do mecânico pela API, PostgreSQL real, outbox e relay reais.
 */
@QuarkusTest
@QuarkusTestResource(value = MensageriaEmMemoria.class, restrictToAnnotatedClass = true)
@TestSecurity(user = "joao", roles = {"MECANICO", "ADMINISTRADOR"})
class ExecucaoFluxoIT {

    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Inject
    @Any
    InMemoryConnector conector;

    private InMemorySource<String> comandos;
    private InMemorySink<String> eventos;

    @BeforeEach
    void canais() {
        comandos = conector.source("comandos");
        eventos = conector.sink("eventos");
    }

    @Test
    @DisplayName("fila → diagnóstico com reserva → liberação (pagamento) → reparo → finalização")
    void fluxoCompleto() {
        String peca = cadastrarPeca(5);
        String servico = cadastrarServico();
        String os = abrirOrdemExecucao();

        given().get("/fila?etapa=DIAGNOSTICO").then().statusCode(200).body("osId", hasItem(os));
        given().post("/ordens-execucao/" + os + "/diagnostico/iniciar").then().statusCode(200)
                .body("status", equalTo("EM_DIAGNOSTICO")).body("mecanicoDiagnostico", equalTo("joao"));
        esperarEvento(os, "DiagnosticoIniciado");

        given().contentType(ContentType.JSON).body("{\"servicoId\": \"" + servico + "\"}")
                .post("/ordens-execucao/" + os + "/itens/servicos").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"pecaId\": \"" + peca + "\", \"quantidade\": 2}")
                .post("/ordens-execucao/" + os + "/itens/pecas").then().statusCode(200).body("itens", hasSize(2));
        given().post("/ordens-execucao/" + os + "/diagnostico/concluir").then().statusCode(200)
                .body("status", equalTo("AGUARDANDO_LIBERACAO"))
                .body("itens.find { it.tipo == 'PECA' }.reservaId", notNullValue())
                .body("total", equalTo(310.0f));

        JsonObject concluido = esperarEvento(os, "DiagnosticoConcluido");
        assertEquals(2, concluido.getJsonObject("payload").getJsonArray("itens").size());
        assertEquals(1, concluido.getJsonObject("payload").getJsonArray("reservas").size());
        given().get("/pecas/" + peca).then().body("saldoDisponivel", equalTo(3));

        enviar(os, "LiberarParaReparo", new JsonObject().put("orcamentoId", UUID.randomUUID().toString()));
        esperarEvento(os, "ReparoLiberado");
        given().get("/pecas/" + peca).then().body("quantidadeTotal", equalTo(3));
        given().get("/fila?etapa=REPARO").then().body("osId", hasItem(os));

        given().post("/ordens-execucao/" + os + "/reparo/iniciar").then().statusCode(200);
        esperarEvento(os, "ReparoIniciado");
        given().post("/ordens-execucao/" + os + "/reparo/finalizar").then().statusCode(200)
                .body("status", equalTo("FINALIZADA"));
        esperarEvento(os, "ReparoFinalizado");

        String metricas = given().get("/q/metrics").then().extract().asString();
        assertTrue(metricas.contains("oficina_execucao_etapa_duracao_seconds"), "tempo por etapa medido");
    }

    @Test
    @DisplayName("compensação: CancelarOrdemExecucao devolve as peças reservadas")
    void cancelamentoLiberaReservas() {
        String peca = cadastrarPeca(2);
        String os = abrirOrdemExecucao();
        given().post("/ordens-execucao/" + os + "/diagnostico/iniciar").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"pecaId\": \"" + peca + "\", \"quantidade\": 2}")
                .post("/ordens-execucao/" + os + "/itens/pecas").then().statusCode(200);
        given().post("/ordens-execucao/" + os + "/diagnostico/concluir").then().statusCode(200);
        given().get("/pecas/" + peca).then().body("saldoDisponivel", equalTo(0));

        enviar(os, "CancelarOrdemExecucao", new JsonObject().put("motivo", "ORCAMENTO_RECUSADO"));

        JsonObject cancelada = esperarEvento(os, "OrdemExecucaoCancelada");
        assertEquals(1, cancelada.getJsonObject("payload").getInteger("reservasLiberadas"));
        given().get("/pecas/" + peca).then().body("saldoDisponivel", equalTo(2));
        given().get("/ordens-execucao/" + os).then().body("status", equalTo("CANCELADA"))
                .body("motivoCancelamento", equalTo("ORCAMENTO_RECUSADO"));
    }

    @Test
    @DisplayName("comando repetido é respondido de novo, sem abrir outra ordem")
    void comandoRepetidoERespondidoDeNovo() {
        String os = abrirOrdemExecucao();

        enviar(os, "AbrirOrdemExecucao", veiculo());

        await().atMost(ESPERA).until(() -> eventosDa(os, "OrdemExecucaoAberta").size() == 2);
        given().get("/fila?etapa=DIAGNOSTICO").then().body("findAll { it.osId == '" + os + "' }", hasSize(1));
    }

    @Test
    void errosDoMecanico() {
        String peca = cadastrarPeca(1);
        String os = abrirOrdemExecucao();

        given().post("/ordens-execucao/" + os + "/diagnostico/concluir").then().statusCode(409);
        given().post("/ordens-execucao/" + os + "/reparo/iniciar").then().statusCode(409);
        given().post("/ordens-execucao/" + os + "/diagnostico/iniciar").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"pecaId\": \"" + peca + "\", \"quantidade\": 5}")
                .post("/ordens-execucao/" + os + "/itens/pecas").then().statusCode(422);
        given().contentType(ContentType.JSON).body("{\"pecaId\": \"" + UUID.randomUUID() + "\", \"quantidade\": 1}")
                .post("/ordens-execucao/" + os + "/itens/pecas").then().statusCode(404);
        given().delete("/ordens-execucao/" + os + "/itens/" + UUID.randomUUID()).then().statusCode(404);
        given().get("/ordens-execucao/" + UUID.randomUUID()).then().statusCode(404);
        enviar(os, "LiberarParaReparo", new JsonObject());
        JsonObject naoLiberado = esperarEvento(os, "ReparoNaoLiberado");
        assertEquals("DIAGNOSTICO_NAO_CONCLUIDO", naoLiberado.getJsonObject("payload").getString("motivo"));
    }

    // ---------- apoio ----------

    private String cadastrarPeca(int quantidade) {
        return given().contentType(ContentType.JSON)
                .body("{\"descricao\": \"Pastilha de freio\", \"valorUnitario\": 95.00, \"quantidadeInicial\": "
                        + quantidade + "}")
                .post("/pecas").then().statusCode(201).extract().path("id");
    }

    private String cadastrarServico() {
        return given().contentType(ContentType.JSON).body("{\"descricao\": \"Alinhamento\", \"valorBase\": 120.00}")
                .post("/servicos").then().statusCode(201).extract().path("id");
    }

    private static JsonObject veiculo() {
        return new JsonObject()
                .put("veiculo", new JsonObject().put("placa", "ABC1D23").put("marca", "Fiat").put("modelo", "Argo")
                        .put("ano", 2022))
                .put("relatoCliente", "Barulho na suspensão");
    }

    private String abrirOrdemExecucao() {
        String os = UUID.randomUUID().toString();
        enviar(os, "AbrirOrdemExecucao", veiculo());
        esperarEvento(os, "OrdemExecucaoAberta");
        return os;
    }

    private void enviar(String osId, String tipo, JsonObject payload) {
        comandos.send(new JsonObject()
                .put("messageId", UUID.randomUUID().toString())
                .put("type", tipo)
                .put("version", 1)
                .put("correlationId", osId)
                .put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString())
                .put("source", "os-service")
                .put("payload", payload.put("osId", osId))
                .encode());
    }

    private List<JsonObject> eventosDa(String osId, String tipo) {
        return eventos.received().stream()
                .map(Message::getPayload)
                .map(JsonObject::new)
                .filter(e -> osId.equals(e.getString("correlationId")) && tipo.equals(e.getString("type")))
                .toList();
    }

    private JsonObject esperarEvento(String osId, String tipo) {
        await().atMost(ESPERA).until(() -> !eventosDa(osId, tipo).isEmpty());
        JsonObject evento = eventosDa(osId, tipo).get(0);
        assertEquals("execucao-service", evento.getString("source"));
        return evento;
    }
}
