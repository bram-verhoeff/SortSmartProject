package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.model.Sorteerstation;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object voor sorteerstations. Gebruikt uitsluitend PreparedStatements.
 */
public class SorteerstationDao {

    private static final String SELECT_BASE = """
            SELECT s.station_id, s.campus_id, c.naam AS campus_naam,
                   s.serienummer, s.locatie_omschrijving, s.verdieping,
                   s.installatiedatum, s.firmware_versie, s.status
            FROM sorteerstation s
            JOIN campus c ON c.campus_id = s.campus_id
            """;

    public List<Sorteerstation> getAlleStations() throws SQLException {
        List<Sorteerstation> resultaat = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(SELECT_BASE + " ORDER BY s.station_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultaat.add(maakStation(rs));
            }
        }
        return resultaat;
    }

    public void voegStationToe(int campusId, String serienummer, String locatie,
                                Integer verdieping, LocalDate installatiedatum,
                                String firmwareVersie) throws SQLException {
        String sql = """
                INSERT INTO sorteerstation
                    (campus_id, serienummer, locatie_omschrijving, verdieping,
                     installatiedatum, firmware_versie)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, campusId);
            ps.setString(2, serienummer);
            ps.setString(3, locatie);
            if (verdieping != null) {
                ps.setInt(4, verdieping);
            } else {
                ps.setNull(4, Types.TINYINT);
            }
            ps.setDate(5, Date.valueOf(installatiedatum));
            ps.setString(6, firmwareVersie);
            ps.executeUpdate();
        }
    }

    public void wijzigStatus(int stationId, String nieuweStatus) throws SQLException {
        String sql = "UPDATE sorteerstation SET status = ? WHERE station_id = ?";
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nieuweStatus);
            ps.setInt(2, stationId);
            ps.executeUpdate();
        }
    }

    private Sorteerstation maakStation(ResultSet rs) throws SQLException {
        Date d = rs.getDate("installatiedatum");
        return new Sorteerstation(
                rs.getInt("station_id"),
                rs.getInt("campus_id"),
                rs.getString("campus_naam"),
                rs.getString("serienummer"),
                rs.getString("locatie_omschrijving"),
                (Integer) (rs.getObject("verdieping")),
                d != null ? d.toLocalDate() : null,
                rs.getString("firmware_versie"),
                rs.getString("status")
        );
    }
}
