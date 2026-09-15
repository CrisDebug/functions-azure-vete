package cl.veterinaria.functions.roles;

import cl.veterinaria.config.OracleConnection;
import graphql.schema.DataFetcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RolResolver {

    public DataFetcher<List<Map<String, Object>>> listarRoles() {
        return environment -> {

            List<Map<String, Object>> roles = new ArrayList<>();

            String sql = "SELECT ID, NOMBRE FROM ROL ORDER BY ID";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet resultSet = statement.executeQuery()
            ) {

                while (resultSet.next()) {

                    roles.add(Map.of(
                            "id", resultSet.getLong("ID"),
                            "nombre", resultSet.getString("NOMBRE")
                    ));
                }

                return roles;
            }
        };
    }

    public DataFetcher<Map<String, Object>> buscarRol() {
        return environment -> {

            Long id = Long.valueOf(
                    environment.getArgument("id").toString()
            );

            String sql =
                    "SELECT ID, NOMBRE FROM ROL WHERE ID = ?";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)
            ) {

                statement.setLong(1, id);

                try (ResultSet resultSet = statement.executeQuery()) {

                    if (!resultSet.next()) {
                        return null;
                    }

                    return Map.of(
                            "id", resultSet.getLong("ID"),
                            "nombre", resultSet.getString("NOMBRE")
                    );
                }
            }
        };
    }

    public DataFetcher<Map<String, Object>> crearRol() {
        return environment -> {

            String nombre = environment.getArgument("nombre");

            String sql =
                    "INSERT INTO ROL (NOMBRE) VALUES (?)";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            sql,
                            new String[]{"ID"}
                    )
            ) {

                statement.setString(1, nombre);
                statement.executeUpdate();

                Long id = null;

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {

                    if (generatedKeys.next()) {
                        id = generatedKeys.getLong(1);
                    }
                }

                if (id == null) {
                    return buscarPorNombre(connection, nombre);
                }

                return buscarPorId(connection, id);
            }
        };
    }

    public DataFetcher<Map<String, Object>> actualizarRol() {
        return environment -> {

            Long id = Long.valueOf(
                    environment.getArgument("id").toString()
            );

            String nombre = environment.getArgument("nombre");

            String sql =
                    "UPDATE ROL SET NOMBRE = ? WHERE ID = ?";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)
            ) {

                statement.setString(1, nombre);
                statement.setLong(2, id);

                int filas = statement.executeUpdate();

                if (filas == 0) {
                    return null;
                }

                return buscarPorId(connection, id);
            }
        };
    }

    public DataFetcher<Boolean> eliminarRol() {
        return environment -> {

            Long id = Long.valueOf(
                    environment.getArgument("id").toString()
            );

            String sql = "DELETE FROM ROL WHERE ID = ?";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)
            ) {

                statement.setLong(1, id);

                return statement.executeUpdate() > 0;
            }
        };
    }

    private Map<String, Object> buscarPorId(
            Connection connection,
            Long id) throws Exception {

        String sql =
                "SELECT ID, NOMBRE FROM ROL WHERE ID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return Map.of(
                        "id", resultSet.getLong("ID"),
                        "nombre", resultSet.getString("NOMBRE")
                );
            }
        }
    }

    private Map<String, Object> buscarPorNombre(
            Connection connection,
            String nombre) throws Exception {

        String sql =
                "SELECT ID, NOMBRE FROM ROL WHERE NOMBRE = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nombre);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return Map.of(
                        "id", resultSet.getLong("ID"),
                        "nombre", resultSet.getString("NOMBRE")
                );
            }
        }
    }
}