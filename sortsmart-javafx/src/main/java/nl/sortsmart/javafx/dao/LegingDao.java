package nl.sortsmart.javafx.dao;

import nl.sortsmart.javafx.util.DatabaseConnector;

import java.math.BigDecimal;
import java.sql.*;

/**
 * Data Access Object voor legingen. Gebruikt uitsluitend PreparedStatements.
 */
public class LegingDao {

    /**
     * Registreert een leging en reset de vulgraad van het bijbehorende vak naar 0
     * in één transactie.
     */
    public void registreerLeging(int stationId, int vaknummer, int medewerkerId,
                                  int vulgraadVoorLeging, BigDecimal gewichtKg) throws SQLException {
        String sqlLeging = """
                INSERT INTO leging (station_id, vaknummer, medewerker_id, vulgraad_voor_leging, gewicht_kg)
                VALUES (?, ?, ?, ?, ?)
                """;
        String sqlVak = "UPDATE vak SET vulgraad_procent = 0 WHERE station_id = ? AND vaknummer = ?";

        try (Connection conn = DatabaseConnector.getVerbinding()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psLeging = conn.prepareStatement(sqlLeging);
                 PreparedStatement psVak    = conn.prepareStatement(sqlVak)) {

                psLeging.setInt(1, stationId);
                psLeging.setInt(2, vaknummer);
                psLeging.setInt(3, medewerkerId);
                psLeging.setInt(4, vulgraadVoorLeging);
                if (gewichtKg != null) {
                    psLeging.setBigDecimal(5, gewichtKg);
                } else {
                    psLeging.setNull(5, Types.DECIMAL);
                }
                psLeging.executeUpdate();

                psVak.setInt(1, stationId);
                psVak.setInt(2, vaknummer);
                psVak.executeUpdate();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
