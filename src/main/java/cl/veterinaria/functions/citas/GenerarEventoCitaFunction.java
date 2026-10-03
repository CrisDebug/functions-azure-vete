package cl.veterinaria.functions.citas;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class GenerarEventoCitaFunction {

    @FunctionName("generarEventoCitaJava")
    public HttpResponseMessage run(

            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,

            final ExecutionContext context) {

        context.getLogger().info("===== generarEventoCitaJava =====");

        String body = request.getBody().orElse("");

        context.getLogger().info("BODY RECIBIDO: " + body);

        if (body.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Body vacío.")
                    .build();
        }

        try {

            // ==========================================================
            // DATOS DE LA CITA
            // ==========================================================

            String citaId = extraerValor(body, "citaId");
            String estado = extraerValor(body, "estado");
            String fechaHora = extraerValor(body, "fechaHora");
            String motivo = extraerValor(body, "motivo");

            if (citaId == null || citaId.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("Falta citaId.")
                        .build();
            }

            if (estado == null || estado.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("Falta estado.")
                        .build();
            }

            // ==========================================================
            // CONFIGURACIÓN EVENT GRID
            // ==========================================================

            String endpoint = System.getenv("EVENT_GRID_ENDPOINT");
            String key = System.getenv("EVENT_GRID_KEY");

            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException(
                        "EVENT_GRID_ENDPOINT no está configurado");
            }

            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                        "EVENT_GRID_KEY no está configurado");
            }

            // ==========================================================
            // CREAR EVENTO CitaCreada
            // ==========================================================

            String eventId = UUID.randomUUID().toString();
            String eventTime = Instant.now().toString();

            String evento = """
                    [
                      {
                        "id": "%s",
                        "eventType": "CitaCreada",
                        "subject": "/citas/%s",
                        "eventTime": "%s",
                        "data": {
                          "citaId": %s,
                          "estado": "%s",
                          "fechaHora": "%s",
                          "motivo": "%s"
                        },
                        "dataVersion": "1.0"
                      }
                    ]
                    """.formatted(
                    eventId,
                    escapeJson(citaId),
                    eventTime,
                    citaId,
                    escapeJson(estado),
                    escapeJson(fechaHora != null ? fechaHora : ""),
                    escapeJson(motivo != null ? motivo : ""));

            context.getLogger().info(
                    "EVENTO CitaCreada GENERADO: " + evento);

            // ==========================================================
            // PUBLICAR EN EVENT GRID
            // ==========================================================

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("aeg-sas-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(evento))
                    .build();

            HttpResponse<String> response = client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            context.getLogger().info(
                    "EVENT GRID STATUS: " + response.statusCode());

            context.getLogger().info(
                    "EVENT GRID RESPONSE: " + response.body());

            if (response.statusCode() >= 200 &&
                    response.statusCode() < 300) {

                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(
                                "{\"mensaje\":\"Evento CitaCreada publicado correctamente\",\"eventId\":\""
                                        + eventId
                                        + "\"}")
                        .build();
            }

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            "Error publicando en Event Grid. HTTP "
                                    + response.statusCode()
                                    + " - "
                                    + response.body())
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "===== ERROR GENERANDO EVENTO =====");

            context.getLogger().severe(e.toString());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            "ERROR: "
                                    + e.getClass().getName()
                                    + " - "
                                    + e.getMessage())
                    .build();
        }
    }

    // ==========================================================
    // EXTRACTOR SIMPLE
    // ==========================================================

    private static String extraerValor(
            String json,
            String campo) {

        String buscar = "\"" + campo + "\"";

        int posicion = json.indexOf(buscar);

        if (posicion == -1) {
            return null;
        }

        int inicio = json.indexOf(":", posicion);

        if (inicio == -1) {
            return null;
        }

        inicio++;

        while (inicio < json.length()
                && Character.isWhitespace(json.charAt(inicio))) {

            inicio++;
        }

        if (inicio >= json.length()) {
            return null;
        }

        // STRING
        if (json.charAt(inicio) == '"') {

            inicio++;

            StringBuilder resultado = new StringBuilder();

            boolean escape = false;

            for (int i = inicio; i < json.length(); i++) {

                char c = json.charAt(i);

                if (escape) {
                    resultado.append(c);
                    escape = false;
                    continue;
                }

                if (c == '\\') {
                    escape = true;
                    continue;
                }

                if (c == '"') {
                    return resultado.toString();
                }

                resultado.append(c);
            }

            return null;
        }

        // NUMBER
        int fin = inicio;

        while (fin < json.length()
                && (Character.isDigit(json.charAt(fin))
                        || json.charAt(fin) == '-')) {

            fin++;
        }

        if (fin > inicio) {
            return json.substring(inicio, fin);
        }

        return null;
    }

    // ==========================================================
    // ESCAPE JSON
    // ==========================================================

    private static String escapeJson(String valor) {

        if (valor == null) {
            return "";
        }

        return valor
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}