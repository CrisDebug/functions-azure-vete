package cl.veterinaria.functions.mascotas;

import graphql.schema.DataFetcher;

import java.util.List;

public class MascotaResolver {

    private final MascotaRepository repository;

    public MascotaResolver(MascotaRepository repository) {
        this.repository = repository;
    }

    public DataFetcher<List<Mascota>> listarMascotas() {
        return environment -> repository.listar();
    }

    public DataFetcher<Mascota> buscarMascota() {
        return environment -> {
            Long id = Long.valueOf(environment.getArgument("id").toString());
            return repository.buscarPorId(id);
        };
    }

    public DataFetcher<Mascota> crearMascota() {
        return environment -> {
            String nombre = environment.getArgument("nombre");
            return repository.crear(nombre);
        };
    }

    public DataFetcher<Mascota> actualizarMascota() {
        return environment -> {
            Long id = Long.valueOf(environment.getArgument("id").toString());
            String nombre = environment.getArgument("nombre");

            return repository.actualizar(id, nombre);
        };
    }

    public DataFetcher<Boolean> eliminarMascota() {
        return environment -> {
            Long id = Long.valueOf(environment.getArgument("id").toString());
            return repository.eliminar(id);
        };
    }
}