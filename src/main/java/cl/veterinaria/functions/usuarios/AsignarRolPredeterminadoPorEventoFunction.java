
package cl.veterinaria.functions.usuarios;

import cl.veterinaria.config.OracleConnection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Function consumidora de Event Grid que asigna el rol predeterminado
 * a un usuario existente en Oracle.
 *
 * Tipo de evento esperado:
 * AsignacionRolPredeterminadoSolicitada
 *
 * Estructura esperada:
 * {
 * "eventType": "AsignacionRolPredeterminadoSolicitada",
 * "data": {
 * "usuarioId": 123
 * }
 * }
 */
public class AsignarRolPredeterminadoPorEventoFunction {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private static final String TIPO_EVENTO = "AsignacionRolPredeterminadoSolicitada";

        private static final String NOMBRE_ROL_PREDETERMINADO = "ROL_PRIMARIO";

        @FunctionName("asignarRolPredeterminadoPorEventoJava")
        public void run(
                        @EventGridTrigger(name = "evento", dataType = "string") String evento,
                        final ExecutionContext context) {

                context.getLogger().info(
                                "Inicio de asignarRolPredeterminadoPorEventoJava.");

                try {
                        // 1. Validar y leer el evento recibido.
                        if (evento == null || evento.isBlank()) {
                                throw new IllegalArgumentException(
                                                "El contenido del evento está vacío.");
                        }

                        JsonNode raiz = MAPPER.readTree(evento);

                        // Event Grid puede entregar los eventos en un arreglo.
                        JsonNode eventoJson = raiz.isArray()
                                        ? (raiz.size() > 0 ? raiz.get(0) : null)
                                        : raiz;

                        if (eventoJson == null || !eventoJson.isObject()) {
                                throw new IllegalArgumentException(
                                                "La estructura del evento no es válida.");
                        }

                        // 2. Validar el tipo de evento.
                        String tipo = eventoJson.path("eventType").asText("");

                        if (!TIPO_EVENTO.equals(tipo)) {
                                context.getLogger().warning(
                                                "Tipo de evento no reconocido: " + tipo);
                                return;
                        }

                        // 3. Obtener y validar el identificador del usuario.
                        JsonNode datos = eventoJson.path("data");
                        JsonNode usuarioIdNode = datos.path("usuarioId");

                        if (!usuarioIdNode.isIntegralNumber()
                                        || !usuarioIdNode.canConvertToLong()
                                        || usuarioIdNode.asLong() <= 0) {

                                throw new IllegalArgumentException(
                                                "El evento debe incluir un usuarioId entero positivo.");
                        }

                        long usuarioId = usuarioIdNode.asLong();

                        // 4. Abrir conexión e iniciar una transacción explícita.
                        try (Connection connection = OracleConnection.getConnection()) {

                                connection.setAutoCommit(false);

                                try {
                                        // 5. Buscar el identificador del rol predeterminado.
                                        int rolId = obtenerRolPredeterminado(connection);

                                        // 6. Comprobar que el usuario exista.
                                        verificarUsuario(connection, usuarioId);

                                        // 7. Asignar el rol al usuario.
                                        asignarRol(connection, usuarioId, rolId);

                                        // 8. Confirmar la transacción.
                                        connection.commit();

                                        context.getLogger().info(
                                                        "Asignación de rol completada. usuarioId="
                                                                        + usuarioId + ", rolId=" + rolId);

                                } catch (Exception e) {
                                        // Revertir los cambios si falla la operación.
                                        try {
                                                connection.rollback();
                                        } catch (Exception errorRollback) {
                                                e.addSuppressed(errorRollback);
                                        }

                                        throw e;
                                }
                        }

                } catch (Exception e) {
                        context.getLogger().severe(
                                        "Error procesando la asignación del rol: "
                                                        + e.getMessage());

                        // Propagar el error para que Azure Functions pueda
                        // registrar el fallo y aplicar su política de reintentos.
                        throw new RuntimeException(
                                        "No se pudo asignar el rol predeterminado.", e);
                }
        }

        /**
         * Busca en Oracle el ID correspondiente a ROL_PRIMARIO.
         */
        private int obtenerRolPredeterminado(
                        Connection connection) throws Exception {

                String sql = "SELECT ID FROM ROL WHERE UPPER(NOMBRE) = ?";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {

                        statement.setString(
                                        1, NOMBRE_ROL_PREDETERMINADO);

                        try (ResultSet resultado = statement.executeQuery()) {

                                if (!resultado.next()) {
                                        throw new IllegalStateException(
                                                        "No existe el rol "
                                                                        + NOMBRE_ROL_PREDETERMINADO
                                                                        + " en Oracle.");
                                }

                                return resultado.getInt("ID");
                        }
                }
        }

        /**
         * Comprueba que el usuario exista antes de actualizarlo.
         */
        private void verificarUsuario(
                        Connection connection,
                        long usuarioId) throws Exception {

                String sql = "SELECT ID FROM USUARIO WHERE ID = ?";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {

                        statement.setLong(1, usuarioId);

                        try (ResultSet resultado = statement.executeQuery()) {

                                if (!resultado.next()) {
                                        throw new IllegalStateException(
                                                        "No existe el usuario ID " + usuarioId
                                                                        + " en Oracle.");
                                }
                        }
                }
        }

        /**
         * Actualiza el rol del usuario identificado por usuarioId.
         */
        private void asignarRol(
                        Connection connection,
                        long usuarioId,
                        int rolId) throws Exception {

                String sql = "UPDATE USUARIO SET ROL_ID = ? WHERE ID = ?";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {

                        statement.setInt(1, rolId);
                        statement.setLong(2, usuarioId);

                        int filasActualizadas = statement.executeUpdate();

                        if (filasActualizadas != 1) {
                                throw new IllegalStateException(
                                                "Se esperaba actualizar un usuario, pero se "
                                                                + "actualizaron "
                                                                + filasActualizadas + " filas.");
                        }
                }
        }
}
