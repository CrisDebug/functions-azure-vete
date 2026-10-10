
package cl.veterinaria.functions.usuarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class UsuariosGraphQLFunction {

        // Permite interpretar correctamente el cuerpo JSON de la petición.
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        // Instancia de GraphQL configurada con el esquema y los resolvers.
        private static final GraphQL GRAPHQL = crearGraphQL();

        @FunctionName("usuariosGraphQL")
        public HttpResponseMessage run(
                        @HttpTrigger(name = "req", methods = {
                                        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "usuariosGraphQL") HttpRequestMessage<Optional<String>> request,
                        final ExecutionContext context) {

                try {
                        // Obtiene el cuerpo JSON enviado en la petición HTTP.
                        String body = request.getBody().orElse("");

                        // Extrae la consulta GraphQL usando Jackson.
                        String query = extraerQuery(body);

                        if (query == null || query.isBlank()) {
                                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                                                .body(Map.of(
                                                                "error",
                                                                "Debe enviar una query GraphQL en el campo query"))
                                                .header("Content-Type", "application/json")
                                                .build();
                        }

                        // Ejecuta la consulta o mutación GraphQL.
                        ExecutionResult resultado = GRAPHQL.execute(query);

                        // Registra los errores de ejecución para facilitar el diagnóstico.
                        List<GraphQLError> errores = resultado.getErrors();

                        if (errores != null && !errores.isEmpty()) {
                                for (GraphQLError error : errores) {
                                        context.getLogger().severe(
                                                        "Error GraphQL: " + error);
                                }
                        }

                        // Convierte el resultado al formato estándar de respuesta GraphQL.
                        Map<String, Object> respuesta = resultado.toSpecification();

                        return request.createResponseBuilder(HttpStatus.OK)
                                        .body(respuesta)
                                        .header("Content-Type", "application/json")
                                        .build();

                } catch (Exception e) {
                        // Registra la excepción completa para identificar el origen del error.
                        context.getLogger().severe(
                                        "Error ejecutando GraphQL: " + e);

                        return request.createResponseBuilder(
                                        HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of(
                                                        "error",
                                                        "Error ejecutando GraphQL",
                                                        "detalle",
                                                        e.getMessage() == null
                                                                        ? "Error no especificado"
                                                                        : e.getMessage()))
                                        .header("Content-Type", "application/json")
                                        .build();
                }
        }

        private static GraphQL crearGraphQL() {

                // Carga la definición del esquema GraphQL.
                String schemaSDL = cargarSchema();

                TypeDefinitionRegistry registry = new SchemaParser().parse(schemaSDL);

                // Crea el resolver que accede a los datos de usuarios.
                UsuarioResolver resolver = new UsuarioResolver();

                // Asocia cada operación del esquema con su método correspondiente.
                RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()

                                .type(
                                                "Query",
                                                builder -> builder
                                                                .dataFetcher(
                                                                                "usuarios",
                                                                                resolver.listarUsuarios())
                                                                .dataFetcher(
                                                                                "usuario",
                                                                                resolver.buscarUsuario()))

                                .type(
                                                "Mutation",
                                                builder -> builder
                                                                .dataFetcher(
                                                                                "crearUsuario",
                                                                                resolver.crearUsuario())
                                                                .dataFetcher(
                                                                                "actualizarUsuario",
                                                                                resolver.actualizarUsuario())
                                                                .dataFetcher(
                                                                                "eliminarUsuario",
                                                                                resolver.eliminarUsuario()))

                                .build();

                // Construye el esquema ejecutable a partir del SDL y los resolvers.
                GraphQLSchema schema = new SchemaGenerator()
                                .makeExecutableSchema(registry, wiring);

                return GraphQL.newGraphQL(schema).build();
        }

        private static String cargarSchema() {

                // Busca el esquema dentro de los recursos del proyecto.
                InputStream inputStream = UsuariosGraphQLFunction.class
                                .getClassLoader()
                                .getResourceAsStream(
                                                "graphql/usuarios.graphqls");

                if (inputStream == null) {
                        throw new IllegalStateException(
                                        "No se encontró graphql/usuarios.graphqls");
                }

                // Lee el contenido completo del archivo del esquema.
                try (
                                Scanner scanner = new Scanner(
                                                inputStream,
                                                StandardCharsets.UTF_8)) {
                        scanner.useDelimiter("\\A");

                        return scanner.hasNext()
                                        ? scanner.next()
                                        : "";
                }
        }

        private static String extraerQuery(String body) {

                // Comprueba que la petición tenga contenido.
                if (body == null || body.isBlank()) {
                        return null;
                }

                try {
                        // Jackson interpreta correctamente comillas, barras y saltos de línea
                        // escapados dentro de la propiedad JSON "query".
                        JsonNode json = OBJECT_MAPPER.readTree(body);

                        if (json == null || !json.isObject()) {
                                throw new IllegalArgumentException(
                                                "El cuerpo debe ser un objeto JSON");
                        }

                        JsonNode queryNode = json.get("query");

                        if (queryNode == null
                                        || queryNode.isNull()
                                        || !queryNode.isTextual()) {
                                return null;
                        }

                        // Devuelve el texto GraphQL ya decodificado desde el JSON.
                        return queryNode.asText();

                } catch (Exception e) {
                        throw new IllegalArgumentException(
                                        "No se pudo interpretar el JSON de la petición: "
                                                        + e.getMessage(),
                                        e);
                }
        }
}
