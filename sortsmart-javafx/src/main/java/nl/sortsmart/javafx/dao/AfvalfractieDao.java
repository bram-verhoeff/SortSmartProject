package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.model.Afvalfractie;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object voor afvalfracties.
 */
public class AfvalfractieDao {

    public List<Afvalfractie> getAlleFracties() throws SQLException {
        String sql = "SELECT fractie_id, naam, kleurcode, omschrijving FROM afvalfractie ORDER BY fractie_id";
        List<Afvalfractie> lijst = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lijst.add(new Afvalfractie(
                        rs.getInt("fractie_id"),
                        rs.getString("naam"),
                        rs.getString("kleurcode"),
                        rs.getString("omschrijving")
                ));
            }
        }
        return lijst;
    }
}
