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
import java.util.Optional;

public class CrearUsuarioFunction {

    @FunctionName("crearUsuarioJava")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("crearUsuarioJava proceso una solicitud.");

        Optional<String> body = request.getBody();

        if (body.isEmpty() || body.get().isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El cuerpo de la solicitud es obligatorio.")
                    .build();
        }

        String contenido = body.get();

        String nombre = extraerCampo(contenido, "nombre");
        String email = extraerCampo(contenido, "email");
        String password = extraerCampo(contenido, "password");
        String rolIdTexto = extraerCampo(contenido, "rolId");

        if (nombre == null || nombre.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El campo nombre es obligatorio.")
                    .build();
        }

        if (email == null || email.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El campo email es obligatorio.")
                    .build();
        }

        if (password == null || password.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El campo password es obligatorio.")
                    .build();
        }

        if (rolIdTexto == null || rolIdTexto.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El campo rolId es obligatorio.")
                    .build();
        }

        int rolId;

        try {
            rolId = Integer.parseInt(rolIdTexto);
        } catch (NumberFormatException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El campo rolId debe ser numérico.")
                    .build();
        }

        String sql = "INSERT INTO USUARIO (NOMBRE, EMAIL, PASSWORD, ROL_ID) VALUES (?, ?, ?, ?)";

        try (Connection connection = OracleConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nombre);
            statement.setString(2, email);
            statement.setString(3, password);
            statement.setInt(4, rolId);

            statement.executeUpdate();

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body("Usuario creado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al crear usuario: " + e.getMessage());

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el usuario.")
                    .build();
        }
    }

    private String extraerCampo(String body, String campo) {

        String patron = "\"" + campo + "\"\\s*:\\s*\"?([^\",}]+)\"?";

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patron);

        java.util.regex.Matcher matcher = pattern.matcher(body);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }
}
