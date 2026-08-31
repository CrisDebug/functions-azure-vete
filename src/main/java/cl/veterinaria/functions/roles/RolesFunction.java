package cl.veterinaria.functions.roles;

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

public class RolesFunction {

    /*
     * ============================================================
     * CRUD DE ROLES
     * ============================================================
     *
     * Una sola Azure Function administra el recurso ROL.
     *
     * GET -> Listar roles
     * POST -> Crear rol
     * PUT -> Actualizar rol
     * DELETE -> Eliminar rol
     *
     * Actualmente implementados:
     * GET + POST
     * ============================================================
     */

    @FunctionName("rolesJava")
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
         * ENRUTAMIENTO DE PETICIONES HTTP
         * ========================================================
         *
         * La misma Function recibe las peticiones.
         * El método HTTP determina qué operación ejecutar.
         */

        if (request.getHttpMethod() == HttpMethod.GET) {

            return listarRoles(request, context);

        } else if (request.getHttpMethod() == HttpMethod.POST) {

            return crearRol(request, context);
        } else if (request.getHttpMethod() == HttpMethod.PUT) {

            return actualizarRol(request, context);
        } else if (request.getHttpMethod() == HttpMethod.DELETE) {

            return eliminarRol(request, context);
        }
        return request.createResponseBuilder(
                HttpStatus.METHOD_NOT_ALLOWED)
                .body("Método HTTP no permitido.")
                .build();
    }

    /*
     * ============================================================
     * MÉTODO HTTP: GET
     * OPERACIÓN: LISTAR ROLES
     * ============================================================
     *
     * GET /api/rolesJava
     */

    private HttpResponseMessage listarRoles(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "rolesJava proceso una solicitud GET.");

        String sql = "SELECT ID, NOMBRE FROM ROL ORDER BY ID";

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
                        .append("\"}");

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
                    "Error al consultar roles: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("ERROR ORACLE: " + e.getClass().getName() + " - " + e.getMessage())
                    .build();
        }
    }

    /*
     * ============================================================
     * MÉTODO HTTP: POST
     * OPERACIÓN: CREAR ROL
     * ============================================================
     *
     * POST /api/rolesJava
     *
     * Body:
     *
     * {
     * "nombre": "Veterinario"
     * }
     */

    private HttpResponseMessage crearRol(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "rolesJava proceso una solicitud POST.");

        Optional<String> body = request.getBody();

        // Validar que exista cuerpo

        if (body.isEmpty()
                || body.get().isBlank()) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body(
                            "El cuerpo de la solicitud es obligatorio.")
                    .build();
        }

        // Extraer nombre

        String nombre = extraerNombre(body.get());

        // Validar nombre

        if (nombre == null
                || nombre.isBlank()) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body(
                            "El campo nombre es obligatorio.")
                    .build();
        }

        String sql = "INSERT INTO ROL (NOMBRE) VALUES (?)";

        try (
                Connection connection = OracleConnection.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nombre);

            statement.executeUpdate();

            context.getLogger().info(
                    "Rol creado correctamente: "
                            + nombre);

            return request.createResponseBuilder(
                    HttpStatus.CREATED)
                    .body(
                            "Rol creado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al crear rol: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            "Error al crear el rol.")
                    .build();
        }
    }

    /*
     * ============================================================
     * UTILIDAD
     * EXTRAER NOMBRE DEL JSON
     * ============================================================
     */

    private String extraerNombre(String body) {

        String limpio = body.trim();

        limpio = limpio
                .replace("{", "")
                .replace("}", "")
                .replace("\"", "");

        String[] partes = limpio.split(":");

        if (partes.length < 2) {
            return null;
        }

        return partes[1].trim();
    }

    /*
     * ============================================================
     * MÉTODO HTTP: PUT
     * OPERACIÓN: ACTUALIZAR ROL
     * ============================================================
     *
     * Endpoint:
     * PUT http://localhost:7071/api/rolesJava
     *
     * Body:
     * {
     * "id": 2,
     * "nombre": "Veterinario Senior"
     * }
     */

    private HttpResponseMessage actualizarRol(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "rolesJava proceso una solicitud PUT.");

        Optional<String> body = request.getBody();

        // Validar cuerpo
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

        // Extraer ID y nombre
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
        }

        // Validar datos
        if (id == null || nombre == null || nombre.isBlank()) {

            return request.createResponseBuilder(
                    HttpStatus.BAD_REQUEST)
                    .body("Los campos id y nombre son obligatorios.")
                    .build();
        }

        String sql = "UPDATE ROL SET NOMBRE = ? WHERE ID = ?";

        try (
                Connection connection = OracleConnection.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nombre);
            statement.setInt(2, id);

            int filasActualizadas = statement.executeUpdate();

            if (filasActualizadas == 0) {

                return request.createResponseBuilder(
                        HttpStatus.NOT_FOUND)
                        .body("El rol no existe.")
                        .build();
            }

            context.getLogger().info(
                    "Rol actualizado correctamente: " + id);

            return request.createResponseBuilder(
                    HttpStatus.OK)
                    .body("Rol actualizado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al actualizar rol: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar el rol.")
                    .build();
        }
    }

    /*
     * ============================================================
     * MÉTODO HTTP: DELETE
     * OPERACIÓN: ELIMINAR ROL
     * ============================================================
     *
     * Endpoint:
     * DELETE http://localhost:7071/api/rolesJava?id=2
     *
     * El ID se recibe mediante Query Parameter.
     */

    private HttpResponseMessage eliminarRol(
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        context.getLogger().info(
                "rolesJava proceso una solicitud DELETE.");

        // Obtener ID desde el Query Parameter
        String idParametro = request.getQueryParameters().get("id");

        // Validar que exista el ID
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

        String sql = "DELETE FROM ROL WHERE ID = ?";

        try (
                Connection connection = OracleConnection.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            int filasEliminadas = statement.executeUpdate();

            if (filasEliminadas == 0) {

                return request.createResponseBuilder(
                        HttpStatus.NOT_FOUND)
                        .body("El rol no existe.")
                        .build();
            }

            context.getLogger().info(
                    "Rol eliminado correctamente: " + id);

            return request.createResponseBuilder(
                    HttpStatus.OK)
                    .body("Rol eliminado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al eliminar rol: "
                            + e.getMessage());

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al eliminar el rol.")
                    .build();
        }
    }

}