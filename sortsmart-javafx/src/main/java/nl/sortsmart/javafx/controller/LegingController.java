package nl.sortsmart.javafx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import nl.sortsmart.javafx.dao.LegingDao;
import nl.sortsmart.javafx.dao.MedewerkerDao;
import nl.sortsmart.javafx.dao.SorteerstationDao;
import nl.sortsmart.javafx.dao.VakDao;
import nl.sortsmart.javafx.model.Medewerker;
import nl.sortsmart.javafx.model.Sorteerstation;
import nl.sortsmart.javafx.model.Vak;
import nl.sortsmart.javafx.util.WaarschuwingUtil;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller voor Leging registreren: station, vak en medewerker kiezen,
 * vulgraad en gewicht invullen, daarna vulgraad van het vak op 0.
 */
public class LegingController implements Initializable {

    @FXML private ComboBox<Sorteerstation> cbStation;
    @FXML private ComboBox<Vak>            cbVak;
    @FXML private ComboBox<Medewerker>     cbMedewerker;
    @FXML private TextField                tfVulgraad;
    @FXML private TextField                tfGewicht;

    private final SorteerstationDao stationDao   = new SorteerstationDao();
    private final VakDao            vakDao        = new VakDao();
    private final MedewerkerDao     medewerkerDao = new MedewerkerDao();
    private final LegingDao         legingDao     = new LegingDao();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Vak-combobox afhankelijk van stationkeuze
        cbStation.valueProperty().addListener((obs, oud, nieuw) -> {
            if (nieuw != null) {
                laadVakken(nieuw.getStationId());
            } else {
                cbVak.setItems(FXCollections.emptyObservableList());
            }
        });

        // Vulgraad automatisch invullen als vak gekozen is
        cbVak.valueProperty().addListener((obs, oud, nieuw) -> {
            if (nieuw != null) {
                tfVulgraad.setText(String.valueOf(nieuw.getVulgraadProcent()));
            }
        });

        laadStations();
        laadMedewerkers();
    }

    private void laadStations() {
        try {
            List<Sorteerstation> stations = stationDao.getAlleStations();
            cbStation.setItems(FXCollections.observableArrayList(stations));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon stations niet laden:\n" + e.getMessage());
        }
    }

    private void laadVakken(int stationId) {
        try {
            List<Vak> vakken = vakDao.getVakkenMetFractie(stationId);
            cbVak.setItems(FXCollections.observableArrayList(vakken));
            cbVak.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Vak v) {
                    return v == null ? "" : "Vak " + v.getVaknummer() + " – " + v.getFractieNaam();
                }
                @Override public Vak fromString(String s) { return null; }
            });
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon vakken niet laden:\n" + e.getMessage());
        }
    }

    private void laadMedewerkers() {
        try {
            List<Medewerker> medewerkers = medewerkerDao.getAlleMedewerkers();
            cbMedewerker.setItems(FXCollections.observableArrayList(medewerkers));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon medewerkers niet laden:\n" + e.getMessage());
        }
    }

    @FXML
    private void registreerLeging() {
        Sorteerstation station   = cbStation.getValue();
        Vak vak                  = cbVak.getValue();
        Medewerker medewerker    = cbMedewerker.getValue();
        String vulgraadTekst     = tfVulgraad.getText().trim();
        String gewichtTekst      = tfGewicht.getText().trim();

        // Validatie
        if (station == null)  { WaarschuwingUtil.toonWaarschuwing("Invoer", "Selecteer een station."); return; }
        if (vak == null)      { WaarschuwingUtil.toonWaarschuwing("Invoer", "Selecteer een vak."); return; }
        if (medewerker == null){ WaarschuwingUtil.toonWaarschuwing("Invoer", "Selecteer een medewerker."); return; }

        int vulgraad;
        try {
            vulgraad = Integer.parseInt(vulgraadTekst);
            if (vulgraad < 0 || vulgraad > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            WaarschuwingUtil.toonWaarschuwing("Invoer", "Vulgraad moet een getal zijn tussen 0 en 100."); return;
        }

        BigDecimal gewicht = null;
        if (!gewichtTekst.isEmpty()) {
            try {
                gewicht = new BigDecimal(gewichtTekst.replace(',', '.'));
                if (gewicht.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                WaarschuwingUtil.toonWaarschuwing("Invoer", "Gewicht moet een positief getal zijn (bijv. 3.40)."); return;
            }
        }

        try {
            legingDao.registreerLeging(
                    station.getStationId(),
                    vak.getVaknummer(),
                    medewerker.getMedewerkerId(),
                    vulgraad,
                    gewicht
            );
            WaarschuwingUtil.toonInfo("Geregistreerd",
                    "Leging geregistreerd. Vulgraad van vak " + vak.getVaknummer() + " is op 0% gezet.");
            formulierLeegmaken();
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Leging registreren mislukt:\n" + e.getMessage());
        }
    }

    private void formulierLeegmaken() {
        cbStation.setValue(null);
        cbVak.setValue(null);
        cbMedewerker.setValue(null);
        tfVulgraad.clear();
        tfGewicht.clear();
    }
}
