package cl.veterinaria.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class OracleConnection {

    private static final String URL = "jdbc:oracle:thin:@devdb_medium";
    private static final String USER = "DEV_APP";

    public static Connection getConnection() throws SQLException {

        String password = System.getenv("ORACLE_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new SQLException("ORACLE_DB_PASSWORD no está configurada");
        }

        String tnsAdmin = System.getenv("ORACLE_TNS_ADMIN");

        if (tnsAdmin == null || tnsAdmin.isBlank()) {
            throw new SQLException("ORACLE_TNS_ADMIN no está configurada");
        }

        Properties properties = new Properties();
        properties.setProperty("user", USER);
        properties.setProperty("password", password);
        properties.setProperty("oracle.net.tns_admin", tnsAdmin);

        return DriverManager.getConnection(URL, properties);
    }
}