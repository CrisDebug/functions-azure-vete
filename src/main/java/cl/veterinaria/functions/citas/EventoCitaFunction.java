
package cl.veterinaria.functions.citas;

// ===============================
// Azure Cosmos DB
// ===============================
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;

// ===============================
// Azure Functions
// ===============================
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

/**
 * Azure Function encargada de recibir eventos publicados
 * por Azure Event Grid.
 *
 * Flujo:
 *
 * ms-citas
 * |
 * v
 * Oracle
 * |
 * v
 * Event Grid
 * |
 * v
 * eventoCitaJava
 * |
 * v
 * Cosmos DB
 *
 * La Function mantiene además la lógica necesaria para
 * responder al evento de validación inicial de Event Grid.
 */
public class EventoCitaFunction {

        // ==========================================================
        // CLIENTE COSMOS DB
        // ==========================================================

        /*
         * Cliente reutilizable de Cosmos DB.
         *
         * Se inicializa solamente cuando la Function necesita
         * acceder al contenedor.
         */
        private static CosmosClient cosmosClient;

        /**
         * Obtiene el contenedor de Cosmos DB.
         *
         * Las credenciales NO están escritas en el código.
         *
         * Se obtienen mediante variables de entorno:
         *
         * COSMOS_ENDPOINT
         * COSMOS_KEY
         * COSMOS_DATABASE
         * COSMOS_CONTAINER
         */
        private static CosmosContainer getContainer() {

                if (cosmosClient == null) {

                        cosmosClient = new CosmosClientBuilder()
                                        .endpoint(System.getenv("COSMOS_ENDPOINT"))
                                        .key(System.getenv("COSMOS_KEY"))
                                        .buildClient();
                }

                return cosmosClient
                                .getDatabase(System.getenv("COSMOS_DATABASE"))
                                .getContainer(System.getenv("COSMOS_CONTAINER"));
        }

        // ==========================================================
        // AZURE FUNCTION
        // ==========================================================

        /**
         * Punto de entrada de la Azure Function.
         *
         * Recibe peticiones POST provenientes de Event Grid.
         */
        @FunctionName("eventoCitaJava")
        public HttpResponseMessage run(

                        @HttpTrigger(name = "req", methods = {
                                        HttpMethod.POST
                        }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,

                        final ExecutionContext context) {

                // ======================================================
                // 1. RECEPCIÓN DEL EVENTO
                // ======================================================

                context.getLogger().info(
                                "eventoCitaJava recibió una petición.");

                String body = request.getBody().orElse("");

                context.getLogger().info(
                                "Body recibido: " + body);

                // ======================================================
                // 2. VALIDACIÓN INICIAL DE EVENT GRID
                // ======================================================

                /*
                 * Cuando se crea la suscripción de Event Grid,
                 * Azure puede enviar un SubscriptionValidationEvent.
                 *
                 * La Function debe responder con:
                 *
                 * {
                 * "validationResponse": "..."
                 * }
                 *
                 * para confirmar que el endpoint es válido.
                 */

                if (body.contains("SubscriptionValidationEvent")) {

                        context.getLogger().info(
                                        "Evento de validación de Event Grid detectado.");

                        String validationCode = extraerValor(body, "validationCode");

                        if (validationCode != null) {

                                String response = "{\"validationResponse\":\""
                                                + validationCode
                                                + "\"}";

                                context.getLogger().info(
                                                "Respondiendo validación de Event Grid.");

                                return request.createResponseBuilder(
                                                HttpStatus.OK)
                                                .header(
                                                                "Content-Type",
                                                                "application/json")
                                                .body(response)
                                                .build();
                        }
                }

                // ======================================================
                // 3. PROCESAMIENTO DEL EVENTO CitaCreada
                // ======================================================

                /*
                 * ms-citas publica eventos con:
                 *
                 * eventType = CitaCreada
                 *
                 * Cuando llega este evento extraemos los datos
                 * necesarios para almacenarlos en Cosmos DB.
                 */

                if (body.contains("\"eventType\":\"CitaCreada\"")) {

                        try {

                                // ------------------------------------------------
                                // Extraer información de la cita
                                // ------------------------------------------------

                                String citaId = extraerValor(body, "citaId");

                                String estado = extraerValor(body, "estado");

                                String fechaHora = extraerValor(body, "fechaHora");

                                String motivo = extraerValor(body, "motivo");

                                context.getLogger().info(
                                                "Procesando evento CitaCreada.");

                                context.getLogger().info(
                                                "citaId=" + citaId
                                                                + ", estado=" + estado);

                                // ------------------------------------------------
                                // Validación mínima
                                // ------------------------------------------------

                                if (citaId == null || estado == null) {

                                        context.getLogger().warning(
                                                        "El evento no contiene citaId o estado.");

                                        return request.createResponseBuilder(
                                                        HttpStatus.BAD_REQUEST)
                                                        .body(
                                                                        "Evento sin datos obligatorios.")
                                                        .build();
                                }

                                // ==================================================
                                // 4. CREACIÓN DEL DOCUMENTO COSMOS
                                // ==================================================

                                /*
                                 * El contenedor trazabilidad utiliza:
                                 *
                                 * Partition Key = /estado
                                 *
                                 * Por eso posteriormente utilizamos:
                                 *
                                 * new PartitionKey(estado)
                                 */

                                String documento = """
                                                {
                                                  "id": "cita-%s",
                                                  "citaId": "%s",
                                                  "estado": "%s",
                                                  "fechaHora": "%s",
                                                  "motivo": "%s",
                                                  "origen": "ms-citas",
                                                  "evento": "CitaCreada"
                                                }
                                                """.formatted(
                                                citaId,
                                                citaId,
                                                estado,
                                                fechaHora,
                                                motivo);

                                context.getLogger().info(
                                                "Documento preparado para Cosmos DB.");

                                // ==================================================
                                // 5. PERSISTENCIA EN COSMOS DB
                                // ==================================================

                                CosmosContainer container = getContainer();

                                /*
                                 * Insertamos el documento indicando como
                                 * Partition Key el estado de la cita.
                                 *
                                 * Ejemplo:
                                 *
                                 * estado = PENDIENTE
                                 *
                                 * Partition Key:
                                 *
                                 * /estado = PENDIENTE
                                 */

                                container.createItem(
                                                documento,
                                                new PartitionKey(estado),
                                                new CosmosItemRequestOptions());

                                context.getLogger().info(
                                                "Cita "
                                                                + citaId
                                                                + " registrada correctamente en Cosmos DB.");

                        } catch (Exception e) {

                                // ==================================================
                                // 6. MANEJO DE ERRORES
                                // ==================================================

                                context.getLogger().severe(
                                                "Error guardando evento en Cosmos DB: "
                                                                + e.getMessage());

                                return request.createResponseBuilder(
                                                HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(
                                                                "Error guardando evento en Cosmos DB.")
                                                .build();
                        }
                }

                // ==========================================================
                // 7. RESPUESTA NORMAL
                // ==========================================================

                /*
                 * Event Grid espera una respuesta HTTP exitosa
                 * para considerar procesado el evento.
                 */

                return request.createResponseBuilder(
                                HttpStatus.OK)
                                .body(
                                                "Evento recibido correctamente por eventoCitaJava")
                                .build();
        }

        // ==========================================================
        // MÉTODO AUXILIAR
        // ==========================================================

        /**
         * Extrae un valor simple desde el JSON recibido.
         *
         * Soporta:
         *
         * 1. Valores String
         *
         * "estado": "PENDIENTE"
         *
         * 2. Valores numéricos
         *
         * "citaId": 11
         *
         * Esta implementación es deliberadamente simple para
         * mantener el proyecto liviano y evitar agregar todavía
         * otra dependencia solamente para parsear este evento.
         */
        private String extraerValor(
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

                // ----------------------------------------------------------
                // Ignorar espacios después de ":"
                // ----------------------------------------------------------

                while (inicio < json.length()
                                && Character.isWhitespace(
                                                json.charAt(inicio))) {
                        inicio++;
                }

                if (inicio >= json.length()) {
                        return null;
                }

                // ==========================================================
                // VALOR STRING
                // ==========================================================

                if (json.charAt(inicio) == '"') {

                        inicio++;

                        int fin = json.indexOf(
                                        "\"",
                                        inicio);

                        if (fin == -1) {
                                return null;
                        }

                        return json.substring(
                                        inicio,
                                        fin);
                }

                // ==========================================================
                // VALOR NUMÉRICO
                // ==========================================================

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
