package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.model.Medewerker;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object voor medewerkers. Gebruikt uitsluitend PreparedStatements.
 */
public class MedewerkerDao {

    public List<Medewerker> getAlleMedewerkers() throws SQLException {
        String sql = """
                SELECT medewerker_id, voornaam, tussenvoegsel, achternaam, email, rol
                FROM medewerker
                ORDER BY achternaam, voornaam
                """;
        List<Medewerker> resultaat = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultaat.add(new Medewerker(
                        rs.getInt("medewerker_id"),
                        rs.getString("voornaam"),
                        rs.getString("tussenvoegsel"),
                        rs.getString("achternaam"),
                        rs.getString("email"),
                        rs.getString("rol")
                ));
            }
        }
        return resultaat;
    }
}
