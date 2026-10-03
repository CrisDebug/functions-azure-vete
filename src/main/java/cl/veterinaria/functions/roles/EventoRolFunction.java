package cl.veterinaria.functions.roles;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventoRolFunction {

    private static CosmosClient cosmosClient;

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

    @FunctionName("eventoRolJava")
    public void run(
            @EventGridTrigger(name = "event") String content,
            final ExecutionContext context) {

        context.getLogger().info("===== eventoRolJava =====");
        context.getLogger().info("EVENTO RECIBIDO: " + content);

        try {

            // ==================================================
            // EXTRAER DATOS DEL EVENTO
            // ==================================================

            String nombre = extraerValor(content, "nombre");

            context.getLogger().info("nombre = " + nombre);

            // ==================================================
            // DOCUMENTO DE TRAZABILIDAD
            // ==================================================

            Map<String, Object> documento = new HashMap<>();

            documento.put(
                    "id",
                    UUID.randomUUID().toString());

            documento.put(
                    "eventType",
                    "RolCreado");

            documento.put(
                    "estado",
                    "PROCESADO");

            documento.put(
                    "origen",
                    "rolesJava");

            documento.put(
                    "fechaProcesamiento",
                    Instant.now().toString());

            Map<String, Object> data = new HashMap<>();

            data.put(
                    "nombre",
                    nombre != null ? nombre : "");

            documento.put(
                    "data",
                    data);

            context.getLogger().info(
                    "DOCUMENTO: " + documento);

            context.getLogger().info(
                    "PARTITION KEY: PROCESADO");

            // ==================================================
            // COSMOS CREATE
            // ==================================================

            CosmosContainer container = getContainer();

            CosmosItemResponse<Map<String, Object>> response = container.createItem(
                    documento,
                    new PartitionKey("PROCESADO"),
                    new CosmosItemRequestOptions());

            context.getLogger().info(
                    "COSMOS STATUS: "
                            + response.getStatusCode());

            context.getLogger().info(
                    "COSMOS OK - documento guardado.");

        } catch (Exception e) {

            context.getLogger().severe(
                    "===== ERROR COSMOS =====");

            context.getLogger().severe(
                    e.toString());

            if (e.getMessage() != null) {
                context.getLogger().severe(
                        e.getMessage());
            }

            throw e;
        }
    }

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
