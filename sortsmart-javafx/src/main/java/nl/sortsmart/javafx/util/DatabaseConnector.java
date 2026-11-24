package nl.sortsmart.javafx.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Levert een JDBC-verbinding op basis van db.properties.
 * Geen hardcoded inloggegevens; PreparedStatements verplicht.
 */
public class DatabaseConnector {

    private static final Properties PROPS = new Properties();
    private static String url;
    private static String gebruiker;
    private static String wachtwoord;

    static {
        try (InputStream in = DatabaseConnector.class
                .getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "db.properties niet gevonden in het classpath. " +
                        "Kopieer db.properties.example naar db.properties en vul de gegevens in.");
            }
            PROPS.load(in);
            url       = PROPS.getProperty("db.url");
            gebruiker = PROPS.getProperty("db.gebruiker");
            wachtwoord = PROPS.getProperty("db.wachtwoord");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseConnector() {}

    /**
     * Geeft een nieuwe verbinding. De aanroeper is verantwoordelijk voor sluiten.
     */
    public static Connection getVerbinding() throws SQLException {
        return DriverManager.getConnection(url, gebruiker, wachtwoord);
    }

    /**
     * Controleert of de database bereikbaar is. Geeft true terug bij succes.
     */
    public static boolean testVerbinding() {
        try (Connection conn = getVerbinding()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
