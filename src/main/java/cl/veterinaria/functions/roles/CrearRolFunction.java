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
import java.util.Optional;

public class CrearRolFunction {

    @FunctionName("crearRolJava")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("crearRolJava proceso una solicitud.");

        Optional<String> body = request.getBody();

        if (body.isEmpty() || body.get().isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El cuerpo de la solicitud es obligatorio.")
                    .build();
        }

        String nombre = extraerNombre(body.get());

        if (nombre == null || nombre.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("El campo nombre es obligatorio.")
                    .build();
        }

        String sql = "INSERT INTO ROL (NOMBRE) VALUES (?)";

        try (Connection connection = OracleConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nombre);
            statement.executeUpdate();

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body("Rol creado correctamente.")
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(
                    "Error al crear rol: " + e.getMessage());

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el rol.")
                    .build();
        }
    }

    private String extraerNombre(String body) {

        String limpio = body.trim();

        limpio = limpio.replace("{", "")
                .replace("}", "")
                .replace("\"", "");

        String[] partes = limpio.split(":");

        if (partes.length < 2) {
            return null;
        }

        return partes[1].trim();
    }
}