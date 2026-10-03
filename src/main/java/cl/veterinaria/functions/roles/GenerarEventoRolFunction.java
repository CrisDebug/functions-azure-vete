package cl.veterinaria.functions.roles;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

public class GenerarEventoRolFunction {

    @FunctionName("generarEventoRolJava")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<String> request,
            final ExecutionContext context) {

        context.getLogger().info("===== generarEventoRolJava =====");

        String body = request.getBody();

        if (body == null || body.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El body es obligatorio.")
                    .build();
        }

        String nombre = extraerValor(body, "nombre");

        if (nombre == null || nombre.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El campo nombre es obligatorio.")
                    .build();
        }

        String endpoint = System.getenv("EVENT_GRID_ENDPOINT");
        String key = System.getenv("EVENT_GRID_KEY");

        if (endpoint == null || endpoint.isBlank()
                || key == null || key.isBlank()) {

            context.getLogger().severe(
                    "No están configuradas las variables EVENT_GRID_ENDPOINT o EVENT_GRID_KEY");

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Configuración de Event Grid no disponible.")
                    .build();
        }

        String eventId = UUID.randomUUID().toString();
        String eventTime = Instant.now().toString();

        String evento = """
                [
                  {
                    "id": "%s",
                    "eventType": "RolCreado",
                    "subject": "/roles/%s",
                    "eventTime": "%s",
                    "data": {
                      "nombre": "%s"
                    },
                    "dataVersion": "1.0"
                  }
                ]
                """.formatted(
                eventId,
                escapeJson(nombre),
                eventTime,
                escapeJson(nombre));

        try {

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
                    "Respuesta Event Grid: " + response.statusCode());

            if (response.statusCode() >= 200
                    && response.statusCode() < 300) {

                return request.createResponseBuilder(HttpStatus.OK)
                        .body(
                                "Evento RolCreado publicado correctamente. "
                                        + "eventId: " + eventId)
                        .build();
            }

            context.getLogger().severe(
                    "Error al publicar evento: "
                            + response.statusCode()
                            + " - "
                            + response.body());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al publicar evento en Event Grid.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error: " + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al enviar evento: " + e.getMessage())
                    .build();
        }
    }

    private String extraerValor(String json, String campo) {

        String buscado = "\"" + campo + "\"";

        int posicion = json.indexOf(buscado);

        if (posicion == -1) {
            return null;
        }

        int dosPuntos = json.indexOf(":", posicion);

        if (dosPuntos == -1) {
            return null;
        }

        int inicio = json.indexOf("\"", dosPuntos);

        if (inicio == -1) {
            return null;
        }

        int fin = json.indexOf("\"", inicio + 1);

        if (fin == -1) {
            return null;
        }

        return json.substring(inicio + 1, fin);
    }

    private String escapeJson(String valor) {

        return valor
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}