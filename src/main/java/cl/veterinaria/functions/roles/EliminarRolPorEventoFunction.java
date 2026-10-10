
package cl.veterinaria.functions.roles;

import cl.veterinaria.config.OracleConnection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EliminarRolPorEventoFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("eliminarRolPorEventoJava")
    public void run(
            @EventGridTrigger(name = "event") String content,
            final ExecutionContext context) {

        context.getLogger().info(
                "===== eliminarRolPorEventoJava =====");

        try {
            JsonNode evento = MAPPER.readTree(content);

            // Event Grid normalmente entrega un arreglo de eventos.
            if (evento.isArray()) {
                if (evento.isEmpty()) {
                    throw new IllegalArgumentException(
                            "El arreglo de eventos está vacío.");
                }
                evento = evento.get(0);
            }

            String eventType = evento.path("eventType").asText("");
            JsonNode data = evento.path("data");

            if (!"RolEliminacionSolicitada".equals(eventType)) {
                context.getLogger().warning(
                        "Evento ignorado por tipo no compatible: "
                                + eventType);
                return;
            }

            if (!data.hasNonNull("rolId")
                    || !data.path("rolId").canConvertToInt()) {
                throw new IllegalArgumentException(
                        "El evento no contiene un rolId numérico válido.");
            }

            int rolId = data.path("rolId").asInt();

            if (rolId <= 0) {
                throw new IllegalArgumentException(
                        "El rolId debe ser mayor que cero.");
            }

            eliminarRolYReasignarUsuarios(rolId, context);

        } catch (Exception e) {
            context.getLogger().severe(
                    "Error procesando eliminación de rol: "
                            + e.getMessage());

            // Permite que Azure registre el fallo y aplique
            // sus políticas de reintento o entrega fallida.
            throw new RuntimeException(
                    "No se pudo procesar la eliminación del rol.", e);
        }
    }

    private void eliminarRolYReasignarUsuarios(
            int rolId,
            ExecutionContext context) throws SQLException {

        try (Connection connection = OracleConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String nombreRol;

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT NOMBRE FROM ROL WHERE ID = ? FOR UPDATE")) {
                    ps.setInt(1, rolId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "El rol " + rolId + " no existe.");
                        }
                        nombreRol = rs.getString("NOMBRE");
                    }
                }

                if ("ROL_PRIMARIO".equalsIgnoreCase(nombreRol)
                        || rolId == 1) {
                    throw new SQLException(
                            "No se puede eliminar ROL_PRIMARIO "
                                    + "ni el rol administrador.");
                }

                int rolPrimarioId;

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT ID FROM ROL "
                                + "WHERE UPPER(NOMBRE) = 'ROL_PRIMARIO'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "No existe ROL_PRIMARIO en Oracle.");
                        }
                        rolPrimarioId = rs.getInt("ID");
                    }
                }

                int usuariosReasignados;

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE USUARIO SET ROL_ID = ? WHERE ROL_ID = ?")) {
                    ps.setInt(1, rolPrimarioId);
                    ps.setInt(2, rolId);
                    usuariosReasignados = ps.executeUpdate();
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM ROL WHERE ID = ?")) {
                    ps.setInt(1, rolId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "No se pudo eliminar el rol.");
                    }
                }

                connection.commit();

                context.getLogger().info(
                        "Rol eliminado por evento: " + nombreRol
                                + " (ID " + rolId + "). Usuarios reasignados: "
                                + usuariosReasignados);

            } catch (Exception e) {
                connection.rollback();

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                throw new SQLException(
                        "Falló la transacción de eliminación.", e);
            }
        }
    }
}
