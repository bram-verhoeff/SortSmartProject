package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.model.Campus;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object voor campussen. Gebruikt uitsluitend PreparedStatements.
 */
public class CampusDao {

    public List<Campus> getAlleCampussen() throws SQLException {
        String sql = "SELECT campus_id, naam, adres, postcode, plaats FROM campus ORDER BY naam";
        List<Campus> resultaat = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultaat.add(new Campus(
                        rs.getInt("campus_id"),
                        rs.getString("naam"),
                        rs.getString("adres"),
                        rs.getString("postcode"),
                        rs.getString("plaats")
                ));
            }
        }
        return resultaat;
    }
}
