package cl.veterinaria.functions.usuarios;

import cl.veterinaria.config.OracleConnection;
import graphql.schema.DataFetcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UsuarioResolver {

    public DataFetcher<List<Map<String, Object>>> listarUsuarios() {
        return environment -> {

            List<Map<String, Object>> usuarios = new ArrayList<>();

            String sql = "SELECT u.ID, u.NOMBRE, u.EMAIL, u.ROL_ID, r.NOMBRE AS ROL " +
                    "FROM USUARIO u " +
                    "INNER JOIN ROL r ON u.ROL_ID = r.ID " +
                    "ORDER BY u.ID";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    usuarios.add(Map.of(
                            "id", resultSet.getLong("ID"),
                            "nombre", resultSet.getString("NOMBRE"),
                            "email", resultSet.getString("EMAIL"),
                            "rolId", resultSet.getLong("ROL_ID"),
                            "rol", resultSet.getString("ROL")));
                }

                return usuarios;
            }
        };
    }

    public DataFetcher<Map<String, Object>> buscarUsuario() {
        return environment -> {

            Long id = Long.valueOf(
                    environment.getArgument("id").toString());

            String sql = "SELECT u.ID, u.NOMBRE, u.EMAIL, u.ROL_ID, r.NOMBRE AS ROL " +
                    "FROM USUARIO u " +
                    "INNER JOIN ROL r ON u.ROL_ID = r.ID " +
                    "WHERE u.ID = ?";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setLong(1, id);

                try (ResultSet resultSet = statement.executeQuery()) {

                    if (!resultSet.next()) {
                        return null;
                    }

                    return Map.of(
                            "id", resultSet.getLong("ID"),
                            "nombre", resultSet.getString("NOMBRE"),
                            "email", resultSet.getString("EMAIL"),
                            "rolId", resultSet.getLong("ROL_ID"),
                            "rol", resultSet.getString("ROL"));
                }
            }
        };
    }

    public DataFetcher<Map<String, Object>> crearUsuario() {
        return environment -> {

            String nombre = environment.getArgument("nombre");
            String email = environment.getArgument("email");
            String password = environment.getArgument("password");
            Integer rolId = Integer.valueOf(
                    environment.getArgument("rolId").toString());

            String sql = "INSERT INTO USUARIO " +
                    "(NOMBRE, EMAIL, PASSWORD, ROL_ID) " +
                    "VALUES (?, ?, ?, ?)";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            sql,
                            new String[] { "ID" })) {

                statement.setString(1, nombre);
                statement.setString(2, email);
                statement.setString(3, password);
                statement.setInt(4, rolId);

                statement.executeUpdate();
                System.out.println("Usuario insertado correctamente: " + email);
                Long id = null;

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        id = generatedKeys.getLong(1);
                    }
                }

                if (id == null) {
                    return buscarPorEmail(connection, email);
                }

                return buscarPorId(connection, id);

            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        };
    }

    public DataFetcher<Map<String, Object>> actualizarUsuario() {
        return environment -> {

            Long id = Long.valueOf(
                    environment.getArgument("id").toString());

            String nombre = environment.getArgument("nombre");
            String email = environment.getArgument("email");
            String password = environment.getArgument("password");
            Integer rolId = Integer.valueOf(
                    environment.getArgument("rolId").toString());

            String sql = "UPDATE USUARIO SET " +
                    "NOMBRE = ?, EMAIL = ?, PASSWORD = ?, ROL_ID = ? " +
                    "WHERE ID = ?";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, nombre);
                statement.setString(2, email);
                statement.setString(3, password);
                statement.setInt(4, rolId);
                statement.setLong(5, id);

                int filas = statement.executeUpdate();

                if (filas == 0) {
                    return null;
                }

                return buscarPorId(connection, id);
            }
        };
    }

    public DataFetcher<Boolean> eliminarUsuario() {
        return environment -> {

            Long id = Long.valueOf(
                    environment.getArgument("id").toString());

            String sql = "DELETE FROM USUARIO WHERE ID = ?";

            try (
                    Connection connection = OracleConnection.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setLong(1, id);

                return statement.executeUpdate() > 0;
            }
        };
    }

    private Map<String, Object> buscarPorId(
            Connection connection,
            Long id) throws Exception {

        String sql = "SELECT u.ID, u.NOMBRE, u.EMAIL, u.ROL_ID, r.NOMBRE AS ROL " +
                "FROM USUARIO u " +
                "INNER JOIN ROL r ON u.ROL_ID = r.ID " +
                "WHERE u.ID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return Map.of(
                        "id", resultSet.getLong("ID"),
                        "nombre", resultSet.getString("NOMBRE"),
                        "email", resultSet.getString("EMAIL"),
                        "rolId", resultSet.getLong("ROL_ID"),
                        "rol", resultSet.getString("ROL"));
            }
        }
    }

    private Map<String, Object> buscarPorEmail(
            Connection connection,
            String email) throws Exception {

        String sql = "SELECT u.ID, u.NOMBRE, u.EMAIL, u.ROL_ID, r.NOMBRE AS ROL " +
                "FROM USUARIO u " +
                "INNER JOIN ROL r ON u.ROL_ID = r.ID " +
                "WHERE u.EMAIL = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return Map.of(
                        "id", resultSet.getLong("ID"),
                        "nombre", resultSet.getString("NOMBRE"),
                        "email", resultSet.getString("EMAIL"),
                        "rolId", resultSet.getLong("ROL_ID"),
                        "rol", resultSet.getString("ROL"));
            }
        }
    }
}