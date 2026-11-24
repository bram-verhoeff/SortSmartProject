package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.model.Melding;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object voor meldingen. Gebruikt uitsluitend PreparedStatements.
 */
public class MeldingDao {

    private static final String SELECT_BASE = """
            SELECT m.melding_id, m.station_id, s.serienummer,
                   m.vaknummer, m.afgehandeld_door, m.type,
                   m.omschrijving, m.tijdstip_gemeld, m.status, m.afgehandeld_op
            FROM melding m
            JOIN sorteerstation s ON s.station_id = m.station_id
            """;

    /**
     * Alle open meldingen (status = 'Open' of 'InBehandeling').
     */
    public List<Melding> getOpenMeldingen() throws SQLException {
        String sql = SELECT_BASE + " WHERE m.status IN ('Open','InBehandeling') ORDER BY m.tijdstip_gemeld DESC";
        return voerUit(sql);
    }

    /**
     * Alle meldingen (open + afgehandeld).
     */
    public List<Melding> getAlleMeldingen() throws SQLException {
        return voerUit(SELECT_BASE + " ORDER BY m.tijdstip_gemeld DESC");
    }

    /**
     * Handelt een melding af: zet status op 'Afgehandeld', vult afgehandeld_op en afgehandeld_door in.
     * Bedrijfsregel: VakVol-meldingen hebben altijd een vaknummer (gevalideerd bij aanmaken).
     */
    public void handelAf(int meldingId, int medewerkerId) throws SQLException {
        String sql = """
                UPDATE melding
                SET status = 'Afgehandeld',
                    afgehandeld_op = NOW(),
                    afgehandeld_door = ?
                WHERE melding_id = ?
                """;
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medewerkerId);
            ps.setInt(2, meldingId);
            ps.executeUpdate();
        }
    }

    private List<Melding> voerUit(String sql) throws SQLException {
        List<Melding> resultaat = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp gemeld = rs.getTimestamp("tijdstip_gemeld");
                Timestamp afgehandeld = rs.getTimestamp("afgehandeld_op");
                resultaat.add(new Melding(
                        rs.getInt("melding_id"),
                        rs.getInt("station_id"),
                        rs.getString("serienummer"),
                        rs.getObject("vaknummer") != null ? rs.getInt("vaknummer") : null,
                        rs.getObject("afgehandeld_door") != null ? rs.getInt("afgehandeld_door") : null,
                        rs.getString("type"),
                        rs.getString("omschrijving"),
                        gemeld != null ? gemeld.toLocalDateTime() : null,
                        rs.getString("status"),
                        afgehandeld != null ? afgehandeld.toLocalDateTime() : null
                ));
            }
        }
        return resultaat;
    }
}
