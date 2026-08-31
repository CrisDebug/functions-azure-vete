package cl.veterinaria.functions.usuarios;

import cl.veterinaria.config.OracleConnection;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class UsuariosFunction {

    /*
     * ============================================================
     * CRUD DE USUARIOS
     * ============================================================
     *
     * GET -> Listar usuarios
     * POST -> Crear usuario
     * PUT -> Actualizar usuario
     * DELETE -> Eliminar usuario
     *
     * Por ahora implementamos SOLO GET.
     * ============================================================
     */

    @FunctionName("usuariosJava")
    public HttpResponseMessage run(

            @HttpTrigger(name = "req", methods = {
                    HttpMethod.GET,
                    HttpMethod.POST,
                    HttpMethod.PUT,
                    HttpMethod.DELETE
            }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,

            final ExecutionContext context) {

        /*
         * ========================================================
         * MÉTODO HTTP: GET
         * OPERACIÓN: LISTAR USUARIOS
         * ========================================================
         */

        if (request.getHttpMethod() == HttpMethod.GET) {

            return listarUsuarios(request, context);

        } else if (request.getHttpMethod() == HttpMethod.POST) {

            return crearUsuario(request, context);
        } else if (request.getHttpMethod() == HttpMethod.PUT)

        {

            return actualizarUsuario(request, context);
        } else if (request.getHttpMethod() == HttpMethod.DELETE) {

            return eliminarUsuario(request, context);
        }

        return request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED).body("Método HTTP no permitido.").build();

    }

    /*
     * ============================================================
     * MÉTODO HTTP: GET
     * OPERACIÓN: LISTAR USUARIOS
     * ============================================================
     *
     * Endpoint:
     * GET http://localhost:7071/api/usuariosJava
     *
     * Consulta:
     * USUARIO + ROL
     */

    private HttpResponseMessage listarUsuarios(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "usuariosJava proceso una solicitud GET.");

        String sql = "SELECT u.ID, u.NOMBRE, u.EMAIL, u.ROL_ID, r.NOMBRE AS ROL " +
                "FROM USUARIO u " +
                "INNER JOIN ROL r ON u.ROL_ID = r.ID " +
                "ORDER BY u.ID";

        StringBuilder json = new StringBuilder("[");

        boolean primero = true;

        try (
                Connection connection = OracleConnection.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql);

                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {

                if (!primero) {
                    json.append(",");
                }

                json.append("{")
                        .append("\"id\":")
                        .append(resultSet.getLong("ID"))
                        .append(",\"nombre\":\"")
                        .append(resultSet.getString("NOMBRE"))
                        .append("\"")
                        .append(",\"email\":\"")
                        .append(resultSet.getString("EMAIL"))
                        .append("\"")
                        .append(",\"rolId\":")
                        .append(resultSet.getLong("ROL_ID"))
                        .append(",\"rol\":\"")
                        .append(resultSet.getString("ROL"))
                        .append("\"")
                        .append("}");

                primero = false;
            }

            json.append("]");

            return request.createResponseBuilder(
                    HttpStatus.OK)
                    .header(
                            "Content-Type",
                            "application/json")
                    .body(json.toString())
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al consultar usuarios: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            "Error al consultar los usuarios.")
                    .build();
        }
    }

    /*
     * ============================================================
     * MÉTODO HTTP: POST
     * OPERACIÓN: CREAR USUARIO
     * ============================================================
     *
     * Endpoint:
     * POST http://localhost:7071/api/usuariosJava
     *
     * Body:
     * {
     * "nombre": "Maria Lopez",
     * "email": "maria.lopez@test.cl",
     * "password": "123456",
     * "rolId": 1
     * }
     */

    private HttpResponseMessage crearUsuario(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "usuariosJava proceso una solicitud POST.");

        Optional<String> body = request.getBody();

        if (body.isEmpty() || body.get().isBlank()) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body("El cuerpo de la solicitud es obligatorio.")
                    .build();
        }

        String contenido = body.get()
                .replace("{", "")
                .replace("}", "")
                .replace("\"", "");

        String[] campos = contenido.split(",");

        String nombre = null;
        String email = null;
        String password = null;
        Integer rolId = null;

        for (String campo : campos) {

            String[] partes = campo.split(":");

            if (partes.length < 2) {
                continue;
            }

            String clave = partes[0].trim();
            String valor = partes[1].trim();

            if (clave.equals("nombre")) {
                nombre = valor;
            }

            if (clave.equals("email")) {
                email = valor;
            }

            if (clave.equals("password")) {
                password = valor;
            }

            if (clave.equals("rolId")) {
                rolId = Integer.parseInt(valor);
            }
        }

        if (nombre == null || nombre.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()
                || rolId == null) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body(
                            "Los campos nombre, email, password y rolId son obligatorios.")
                    .build();
        }

        String sql = "INSERT INTO USUARIO " +
                "(NOMBRE, EMAIL, PASSWORD, ROL_ID) " +
                "VALUES (?, ?, ?, ?)";

        try (
                Connection connection = OracleConnection.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nombre);
            statement.setString(2, email);
            statement.setString(3, password);
            statement.setInt(4, rolId);

            statement.executeUpdate();

            context.getLogger().info(
                    "Usuario creado correctamente: " + email);

            return request.createResponseBuilder(
                    HttpStatus.CREATED)
                    .body("Usuario creado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al crear usuario: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el usuario.")
                    .build();
        }
    }

    /*
     * ============================================================
     * MÉTODO HTTP: PUT
     * OPERACIÓN: ACTUALIZAR USUARIO
     * ============================================================
     *
     * Endpoint:
     * PUT http://localhost:7071/api/usuariosJava
     *
     * Body:
     * {
     * "id": 2,
     * "nombre": "Maria Lopez Actualizada",
     * "email": "maria.actualizada@test.cl",
     * "password": "123456",
     * "rolId": 1
     * }
     */

    private HttpResponseMessage actualizarUsuario(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "usuariosJava proceso una solicitud PUT.");

        Optional<String> body = request.getBody();

        if (body.isEmpty() || body.get().isBlank()) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body("El cuerpo de la solicitud es obligatorio.")
                    .build();
        }

        String contenido = body.get()
                .replace("{", "")
                .replace("}", "")
                .replace("\"", "");

        String[] campos = contenido.split(",");

        Integer id = null;
        String nombre = null;
        String email = null;
        String password = null;
        Integer rolId = null;

        for (String campo : campos) {

            String[] partes = campo.split(":");

            if (partes.length < 2) {
                continue;
            }

            String clave = partes[0].trim();
            String valor = partes[1].trim();

            if (clave.equals("id")) {
                id = Integer.parseInt(valor);
            }

            if (clave.equals("nombre")) {
                nombre = valor;
            }

            if (clave.equals("email")) {
                email = valor;
            }

            if (clave.equals("password")) {
                password = valor;
            }

            if (clave.equals("rolId")) {
                rolId = Integer.parseInt(valor);
            }
        }

        if (id == null || nombre == null || nombre.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()
                || rolId == null) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body(
                            "Los campos id, nombre, email, password y rolId son obligatorios.")
                    .build();
        }

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
            statement.setInt(5, id);

            int filasActualizadas = statement.executeUpdate();

            if (filasActualizadas == 0) {

                return request.createResponseBuilder(
                        HttpStatus.NOT_FOUND)
                        .body("El usuario no existe.")
                        .build();
            }

            context.getLogger().info(
                    "Usuario actualizado correctamente: " + id);

            return request.createResponseBuilder(
                    HttpStatus.OK)
                    .body("Usuario actualizado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al actualizar usuario: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar el usuario.")
                    .build();
        }
    }

    /*
     * ============================================================
     * MÉTODO HTTP: DELETE
     * OPERACIÓN: ELIMINAR USUARIO
     * ============================================================
     *
     * Endpoint:
     * DELETE http://localhost:7071/api/usuariosJava?id=2
     */

    private HttpResponseMessage eliminarUsuario(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "usuariosJava proceso una solicitud DELETE.");

        String idParametro = request.getQueryParameters().get("id");

        if (idParametro == null || idParametro.isBlank()) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body("El parámetro id es obligatorio.")
                    .build();
        }

        int id;

        try {

            id = Integer.parseInt(idParametro);

        } catch (NumberFormatException e) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body("El parámetro id debe ser numérico.")
                    .build();
        }

        String sql = "DELETE FROM USUARIO WHERE ID = ?";

        try (
                Connection connection = OracleConnection.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            int filasEliminadas = statement.executeUpdate();

            if (filasEliminadas == 0) {

                return request.createResponseBuilder(
                        HttpStatus.NOT_FOUND)
                        .body("El usuario no existe.")
                        .build();
            }

            context.getLogger().info(
                    "Usuario eliminado correctamente: " + id);

            return request.createResponseBuilder(
                    HttpStatus.OK)
                    .body("Usuario eliminado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al eliminar usuario: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al eliminar el usuario.")
                    .build();
        }
    }

}