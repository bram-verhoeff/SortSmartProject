package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.model.Scan;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object voor scans. Gebruikt uitsluitend PreparedStatements.
 */
public class ScanDao {

    /**
     * Laadt de laatste scans, optioneel gefilterd op stationId en/of resultaat.
     * Null-waarden worden genegeerd (geen filter).
     */
    public List<Scan> getScans(Integer stationId, String resultaat, int limiet) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT sc.scan_id, sc.station_id, s.serienummer,
                       sc.itemtype_id, it.naam AS itemtype_naam,
                       sc.tijdstip, sc.betrouwbaarheid, sc.resultaat
                FROM scan sc
                JOIN sorteerstation s ON s.station_id = sc.station_id
                LEFT JOIN itemtype it ON it.itemtype_id = sc.itemtype_id
                WHERE 1=1
                """);
        if (stationId != null) sql.append(" AND sc.station_id = ?");
        if (resultaat != null && !resultaat.isBlank()) sql.append(" AND sc.resultaat = ?");
        sql.append(" ORDER BY sc.tijdstip DESC LIMIT ?");

        List<Scan> resultaatLijst = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (stationId != null) ps.setInt(idx++, stationId);
            if (resultaat != null && !resultaat.isBlank()) ps.setString(idx++, resultaat);
            ps.setInt(idx, limiet);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("tijdstip");
                    resultaatLijst.add(new Scan(
                            rs.getLong("scan_id"),
                            rs.getInt("station_id"),
                            rs.getString("serienummer"),
                            rs.getObject("itemtype_id") != null ? rs.getInt("itemtype_id") : null,
                            rs.getString("itemtype_naam"),
                            ts != null ? ts.toLocalDateTime() : null,
                            rs.getBigDecimal("betrouwbaarheid"),
                            rs.getString("resultaat")
                    ));
                }
            }
        }
        return resultaatLijst;
    }

    /**
     * Voegt een nieuwe scan toe in de tabel scan.
     * Bedrijfsregel in database: als resultaat = 'Gesorteerd', moet itemtypeId NOT NULL zijn.
     */
    public long voegScanToe(int stationId, Integer itemtypeId, BigDecimal betrouwbaarheid, String resultaat) throws SQLException {
        String sql = """
                INSERT INTO scan (station_id, itemtype_id, tijdstip, betrouwbaarheid, resultaat)
                VALUES (?, ?, NOW(), ?, ?)
                """;
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, stationId);
            if (itemtypeId != null) {
                ps.setInt(2, itemtypeId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            if (betrouwbaarheid != null) {
                ps.setBigDecimal(3, betrouwbaarheid);
            } else {
                ps.setNull(3, Types.DECIMAL);
            }
            ps.setString(4, resultaat);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return -1;
    }

    /**
     * Zoekt een itemtype op basis van de herkende naam. Als er geen exacte match is,
     * valt het terug op het eerste bekende itemtype voor die afvalfractie.
     */
    public Integer zoekOfKoppelItemtype(String itemtypeNaam, int fractieId) throws SQLException {
        if (itemtypeNaam != null && !itemtypeNaam.isBlank()) {
            String sql = "SELECT itemtype_id FROM itemtype WHERE LOWER(naam) = LOWER(?) LIMIT 1";
            try (Connection conn = DatabaseConnector.getVerbinding();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, itemtypeNaam.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("itemtype_id");
                    }
                }
            }
        }
        return getEersteItemtypeIdVoorFractie(fractieId);
    }

    /**
     * Haalt het eerste beschikbare itemtype op voor een gegeven afvalfractie.
     */
    public Integer getEersteItemtypeIdVoorFractie(int fractieId) throws SQLException {
        String sql = "SELECT itemtype_id FROM itemtype WHERE fractie_id = ? LIMIT 1";
        try (Connection conn = DatabaseConnector.getVerbinding();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fractieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("itemtype_id");
                }
            }
        }
        return null;
    }
}
