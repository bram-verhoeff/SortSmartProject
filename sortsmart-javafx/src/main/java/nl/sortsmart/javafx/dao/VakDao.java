package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.model.Vak;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object voor vakken. Gebruikt uitsluitend PreparedStatements.
 */
public class VakDao {

    /**
     * Haalt alle vakken op voor een gegeven station, inclusief fractie-informatie.
     */
    public List<Vak> getVakkenVoorStation(int stationId) throws SQLException {
        String sql = """
                SELECT v.station_id, v.vaknummer, v.fractie_id,
                       af.naam AS fractie_naam, af.kleurcode,
                       v.capaciteit_liter, v.vulgraad_procent
                FROM vak v
                JOIN afvalfractie af ON af.fractie_id = v.fractie_id
                WHERE v.station_id = ?
                ORDER BY v.vaknummer
                """;
        List<Vak> resultaat = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultaat.add(new Vak(
                            rs.getInt("station_id"),
                            rs.getInt("vaknummer"),
                            rs.getInt("fractie_id"),
                            rs.getString("fractie_naam"),
                            rs.getString("kleurcode"),
                            rs.getInt("capaciteit_liter"),
                            rs.getInt("vulgraad_procent")
                    ));
                }
            }
        }
        return resultaat;
    }

    /**
     * Zet de vulgraad van een vak op 0 na een leging.
     */
    public void resetVulgraad(int stationId, int vaknummer) throws SQLException {
        String sql = "UPDATE vak SET vulgraad_procent = 0 WHERE station_id = ? AND vaknummer = ?";
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stationId);
            ps.setInt(2, vaknummer);
            ps.executeUpdate();
        }
    }

    /**
     * Haalt de vakken op van een station als keuzelijst voor leging.
     */
    public List<Vak> getVakkenMetFractie(int stationId) throws SQLException {
        return getVakkenVoorStation(stationId);
    }

    /**
     * Verhoogt de vulgraad van het vak dat gekoppeld is aan de gegeven fractie bij een station.
     * De vulgraad wordt gemaximaliseerd op 100%.
     */
    public void verhoogVulgraadVoorFractie(int stationId, int fractieId, int percentageVerhoging) throws SQLException {
        String sql = """
                UPDATE vak
                SET vulgraad_procent = LEAST(100, vulgraad_procent + ?)
                WHERE station_id = ? AND fractie_id = ?
                """;
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, percentageVerhoging);
            ps.setInt(2, stationId);
            ps.setInt(3, fractieId);
            ps.executeUpdate();
        }
    }
}
