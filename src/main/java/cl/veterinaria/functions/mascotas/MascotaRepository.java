package cl.veterinaria.functions.mascotas;

import cl.veterinaria.config.OracleConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MascotaRepository {

    public List<Mascota> listar() throws SQLException {

        List<Mascota> mascotas = new ArrayList<>();

        String sql = "SELECT ID, NOMBRE FROM MASCOTA ORDER BY ID";

        try (Connection conn = OracleConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                mascotas.add(new Mascota(
                        rs.getLong("ID"),
                        rs.getString("NOMBRE")));
            }
        }

        return mascotas;
    }

    public Mascota buscarPorId(Long id) throws SQLException {

        String sql = "SELECT ID, NOMBRE FROM MASCOTA WHERE ID = ?";

        try (Connection conn = OracleConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Mascota(
                            rs.getLong("ID"),
                            rs.getString("NOMBRE"));
                }
            }
        }

        return null;
    }

    public Mascota crear(String nombre) throws SQLException {

        String sql = "INSERT INTO MASCOTA (NOMBRE) VALUES (?)";

        try (Connection conn = OracleConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        sql,
                        new String[] { "ID" })) {

            stmt.setString(1, nombre);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new Mascota(rs.getLong(1), nombre);
                }
            }
        }

        throw new SQLException("No se pudo obtener el ID generado");
    }

    public Mascota actualizar(Long id, String nombre) throws SQLException {

        String sql = "UPDATE MASCOTA SET NOMBRE = ? WHERE ID = ?";

        try (Connection conn = OracleConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nombre);
            stmt.setLong(2, id);

            int filas = stmt.executeUpdate();

            if (filas == 0) {
                return null;
            }
        }

        return buscarPorId(id);
    }

    public boolean eliminar(Long id) throws SQLException {

        String sql = "DELETE FROM MASCOTA WHERE ID = ?";

        try (Connection conn = OracleConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            return stmt.executeUpdate() > 0;
        }
    }
}