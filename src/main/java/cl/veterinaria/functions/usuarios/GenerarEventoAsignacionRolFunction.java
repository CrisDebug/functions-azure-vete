
package cl.veterinaria.functions.usuarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class GenerarEventoAsignacionRolFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("generarEventoAsignacionRolJava")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS
            )
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        try {
            String body = request.getBody().orElse("");

            if (body.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("El cuerpo de la solicitud es obligatorio.")
                        .build();
            }

            JsonNode json = MAPPER.readTree(body);
            JsonNode usuarioIdNode = json.get("usuarioId");

            if (usuarioIdNode == null
                    || !usuarioIdNode.canConvertToLong()
                    || usuarioIdNode.asLong() <= 0) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("Se requiere un usuarioId numérico positivo.")
                        .build();
            }

            long usuarioId = usuarioIdNode.asLong();

            String endpoint = System.getenv("EVENT_GRID_ENDPOINT");
            String key = System.getenv("EVENT_GRID_KEY");

            if (endpoint == null || endpoint.isBlank()
                    || key == null || key.isBlank()) {
                throw new IllegalStateException(
                        "La configuración de Event Grid está incompleta.");
            }

            String eventId = UUID.randomUUID().toString();

            var evento = MAPPER.createArrayNode();
            var item = evento.addObject();

            item.put("id", eventId);
            item.put("eventType", "AsignacionRolPredeterminadoSolicitada");
            item.put("subject", "/usuarios/" + usuarioId);
            item.put("eventTime", Instant.now().toString());
            item.put("dataVersion", "1.0");
            item.putObject("data").put("usuarioId", usuarioId);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("aeg-sas-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(evento)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            context.getLogger().info(
                    "Publicación de asignación de rol: HTTP "
                            + response.statusCode());

            if (response.statusCode() >= 200
                    && response.statusCode() < 300) {
                return request.createResponseBuilder(HttpStatus.ACCEPTED)
                        .header("Content-Type", "application/json")
                        .body("{\"mensaje\":\"Solicitud publicada\","
                                + "\"eventId\":\"" + eventId + "\"}")
                        .build();
            }

            context.getLogger().severe(
                    "Event Grid rechazó el evento: HTTP "
                            + response.statusCode());

            return request.createResponseBuilder(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo publicar el evento.")
                    .build();

        } catch (Exception e) {
            context.getLogger().severe(
                    "Error al publicar la solicitud: " + e.getMessage());

            return request.createResponseBuilder(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al publicar el evento.")
                    .build();
        }
    }
}
