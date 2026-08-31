package cl.veterinaria.functions.citas;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EventoCitaFunction {

        private static CosmosClient cosmosClient;

        // ==========================================================
        // COSMOS
        // ==========================================================

        private static CosmosContainer getContainer() {

                if (cosmosClient == null) {

                        String endpoint = System.getenv("COSMOS_ENDPOINT");
                        String key = System.getenv("COSMOS_KEY");

                        if (endpoint == null || endpoint.isBlank()) {
                                throw new IllegalStateException(
                                                "COSMOS_ENDPOINT no está configurado");
                        }

                        if (key == null || key.isBlank()) {
                                throw new IllegalStateException(
                                                "COSMOS_KEY no está configurado");
                        }

                        cosmosClient = new CosmosClientBuilder()
                                        .endpoint(endpoint)
                                        .key(key)
                                        .buildClient();
                }

                String database = System.getenv("COSMOS_DATABASE");
                String container = System.getenv("COSMOS_CONTAINER");

                if (database == null || database.isBlank()) {
                        throw new IllegalStateException(
                                        "COSMOS_DATABASE no está configurado");
                }

                if (container == null || container.isBlank()) {
                        throw new IllegalStateException(
                                        "COSMOS_CONTAINER no está configurado");
                }

                return cosmosClient
                                .getDatabase(database)
                                .getContainer(container);
        }

        // ==========================================================
        // HTTP TRIGGER
        // ==========================================================

        @FunctionName("eventoCitaJava")
        public HttpResponseMessage run(

                        @HttpTrigger(name = "req", methods = {
                                        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,

                        final ExecutionContext context) {

                context.getLogger().info(
                                "===== eventoCitaJava =====");

                String body = request.getBody().orElse("");

                context.getLogger().info(
                                "BODY RECIBIDO: " + body);

                if (body.isBlank()) {

                        return request.createResponseBuilder(
                                        HttpStatus.BAD_REQUEST)
                                        .body("Body vacío.")
                                        .build();
                }

                try {

                        // ==================================================
                        // EXTRAER DATOS
                        // ==================================================

                        String citaId = extraerValor(body, "citaId");
                        String estado = extraerValor(body, "estado");
                        String fechaHora = extraerValor(body, "fechaHora");
                        String motivo = extraerValor(body, "motivo");

                        context.getLogger().info(
                                        "citaId = " + citaId);

                        context.getLogger().info(
                                        "estado = " + estado);

                        context.getLogger().info(
                                        "fechaHora = " + fechaHora);

                        context.getLogger().info(
                                        "motivo = " + motivo);

                        // ==================================================
                        // VALIDACIONES
                        // ==================================================

                        if (citaId == null || citaId.isBlank()) {

                                return request.createResponseBuilder(
                                                HttpStatus.BAD_REQUEST)
                                                .body("Falta citaId.")
                                                .build();
                        }

                        if (estado == null || estado.isBlank()) {

                                return request.createResponseBuilder(
                                                HttpStatus.BAD_REQUEST)
                                                .body("Falta estado. Partition Key requerida: /estado")
                                                .build();
                        }

                        // ==================================================
                        // DOCUMENTO
                        // ==================================================

                        Map<String, Object> documento = new HashMap<>();

                        documento.put(
                                        "id",
                                        "cita-" + citaId);

                        documento.put(
                                        "citaId",
                                        Integer.parseInt(citaId));

                        documento.put(
                                        "estado",
                                        estado);

                        documento.put(
                                        "fechaHora",
                                        fechaHora != null
                                                        ? fechaHora
                                                        : "");

                        documento.put(
                                        "motivo",
                                        motivo != null
                                                        ? motivo
                                                        : "");

                        documento.put(
                                        "origen",
                                        "ms-citas");

                        documento.put(
                                        "evento",
                                        "CitaCreada");

                        context.getLogger().info(
                                        "DOCUMENTO: " + documento);

                        context.getLogger().info(
                                        "PARTITION KEY: " + estado);

                        // ==================================================
                        // COSMOS CREATE
                        // ==================================================

                        CosmosContainer container = getContainer();

                        CosmosItemResponse<Map<String, Object>> response = container.createItem(
                                        documento,
                                        new PartitionKey(estado),
                                        new CosmosItemRequestOptions());

                        context.getLogger().info(
                                        "COSMOS STATUS: "
                                                        + response.getStatusCode());

                        context.getLogger().info(
                                        "COSMOS OK - documento guardado.");

                        return request.createResponseBuilder(
                                        HttpStatus.OK)
                                        .header(
                                                        "Content-Type",
                                                        "text/plain")
                                        .body(
                                                        "Cita "
                                                                        + citaId
                                                                        + " guardada correctamente en Cosmos DB.")
                                        .build();

                } catch (Exception e) {

                        context.getLogger().severe(
                                        "===== ERROR COSMOS =====");

                        context.getLogger().severe(
                                        e.toString());

                        if (e.getMessage() != null) {
                                context.getLogger().severe(
                                                e.getMessage());
                        }

                        return request.createResponseBuilder(
                                        HttpStatus.INTERNAL_SERVER_ERROR)
                                        .header(
                                                        "Content-Type",
                                                        "text/plain")
                                        .body(
                                                        "ERROR COSMOS: "
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

                int inicio = json.indexOf(
                                ":",
                                posicion);

                if (inicio == -1) {
                        return null;
                }

                inicio++;

                while (inicio < json.length()
                                && Character.isWhitespace(
                                                json.charAt(inicio))) {

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
                                && (Character.isDigit(
                                                json.charAt(fin))
                                                || json.charAt(fin) == '-')) {

                        fin++;
                }

                if (fin > inicio) {

                        return json.substring(
                                        inicio,
                                        fin);
                }

                return null;
        }
}