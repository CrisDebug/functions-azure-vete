package cl.veterinaria.functions.mascotas;

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
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class MascotasGraphQLFunction {

        private static final GraphQL GRAPHQL = crearGraphQL();

        @FunctionName("mascotasGraphQL")
        public HttpResponseMessage run(
                        @HttpTrigger(name = "req", methods = {
                                        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "mascotasGraphQL") HttpRequestMessage<Optional<String>> request,
                        final ExecutionContext context) {

                try {
                        String body = request.getBody().orElse("");
                        String query = extraerQuery(body);

                        if (query == null || query.isBlank()) {
                                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                                                .body("{\"error\":\"Debe enviar una query GraphQL\"}")
                                                .header("Content-Type", "application/json")
                                                .build();
                        }

                        ExecutionResult resultado = GRAPHQL.execute(query);
                        Map<String, Object> respuesta = resultado.toSpecification();

                        return request.createResponseBuilder(HttpStatus.OK)
                                        .body(respuesta)
                                        .header("Content-Type", "application/json")
                                        .build();

                } catch (Exception e) {

                        context.getLogger().severe(
                                        "Error ejecutando GraphQL: " + e.getMessage());

                        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("{\"error\":\"Error ejecutando GraphQL\"}")
                                        .header("Content-Type", "application/json")
                                        .build();
                }
        }

        private static GraphQL crearGraphQL() {

                String schemaSDL = cargarSchema();

                TypeDefinitionRegistry registry = new SchemaParser().parse(schemaSDL);

                MascotaResolver resolver = new MascotaResolver(new MascotaRepository());

                RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()

                                .type(
                                                "Query",
                                                builder -> builder
                                                                .dataFetcher(
                                                                                "mascotas",
                                                                                resolver.listarMascotas())
                                                                .dataFetcher(
                                                                                "mascota",
                                                                                resolver.buscarMascota()))

                                .type(
                                                "Mutation",
                                                builder -> builder
                                                                .dataFetcher(
                                                                                "crearMascota",
                                                                                resolver.crearMascota())
                                                                .dataFetcher(
                                                                                "actualizarMascota",
                                                                                resolver.actualizarMascota())
                                                                .dataFetcher(
                                                                                "eliminarMascota",
                                                                                resolver.eliminarMascota()))

                                .build();

                GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(
                                registry,
                                wiring);

                return GraphQL.newGraphQL(schema).build();
        }

        private static String cargarSchema() {

                InputStream inputStream = MascotasGraphQLFunction.class
                                .getClassLoader()
                                .getResourceAsStream("graphql/mascotas.graphqls");

                if (inputStream == null) {
                        throw new IllegalStateException(
                                        "No se encontro graphql/mascotas.graphqls");
                }

                try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {

                        scanner.useDelimiter("\\A");

                        return scanner.hasNext()
                                        ? scanner.next()
                                        : "";
                }
        }

        private static String extraerQuery(String body) {

                String marker = "\"query\"";
                int inicio = body.indexOf(marker);

                if (inicio < 0) {
                        return null;
                }

                int dosPuntos = body.indexOf(":", inicio);

                if (dosPuntos < 0) {
                        return null;
                }

                int primeraComilla = body.indexOf("\"", dosPuntos + 1);

                if (primeraComilla < 0) {
                        return null;
                }

                StringBuilder query = new StringBuilder();
                boolean escapado = false;

                for (int i = primeraComilla + 1; i < body.length(); i++) {

                        char c = body.charAt(i);

                        if (escapado) {
                                if (c == '"') {
                                        query.append('"');
                                } else if (c == '\\') {
                                        query.append('\\');
                                } else {
                                        query.append(c);
                                }

                                escapado = false;
                                continue;
                        }

                        if (c == '\\') {
                                escapado = true;
                                continue;
                        }

                        if (c == '"') {
                                break;
                        }

                        query.append(c);
                }

                return query.toString();
        }
}
