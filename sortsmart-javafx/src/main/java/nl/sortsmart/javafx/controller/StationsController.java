package nl.sortsmart.javafx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import nl.sortsmart.javafx.dao.CampusDao;
import nl.sortsmart.javafx.dao.SorteerstationDao;
import nl.sortsmart.javafx.model.Campus;
import nl.sortsmart.javafx.model.Sorteerstation;
import nl.sortsmart.javafx.util.WaarschuwingUtil;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller voor het Stations-scherm: tabel met stations, toevoegen en status wijzigen.
 */
public class StationsController implements Initializable {

    @FXML private TableView<Sorteerstation> tabelStations;
    @FXML private TableColumn<Sorteerstation, String> kolSerienummer;
    @FXML private TableColumn<Sorteerstation, String> kolCampus;
    @FXML private TableColumn<Sorteerstation, String> kolLocatie;
    @FXML private TableColumn<Sorteerstation, String> kolStatus;
    @FXML private TableColumn<Sorteerstation, String> kolFirmware;

    // Formulier toevoegen
    @FXML private ComboBox<Campus> cbCampus;
    @FXML private TextField tfSerienummer;
    @FXML private TextField tfLocatie;
    @FXML private TextField tfVerdieping;
    @FXML private DatePicker dpInstallatiedatum;
    @FXML private TextField tfFirmware;

    // Status wijzigen
    @FXML private ComboBox<String> cbNieuweStatus;

    private final SorteerstationDao stationDao = new SorteerstationDao();
    private final CampusDao campusDao = new CampusDao();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        kolSerienummer.setCellValueFactory(new PropertyValueFactory<>("serienummer"));
        kolCampus.setCellValueFactory(new PropertyValueFactory<>("campusNaam"));
        kolLocatie.setCellValueFactory(new PropertyValueFactory<>("locatieOmschrijving"));
        kolStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        kolFirmware.setCellValueFactory(new PropertyValueFactory<>("firmwareVersie"));

        cbNieuweStatus.setItems(FXCollections.observableArrayList(
                "Actief", "Onderhoud", "Storing", "Inactief"));

        laadCampussen();
        laadStations();
    }

    private void laadCampussen() {
        try {
            List<Campus> campussen = campusDao.getAlleCampussen();
            cbCampus.setItems(FXCollections.observableArrayList(campussen));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon campussen niet laden:\n" + e.getMessage());
        }
    }

    private void laadStations() {
        try {
            List<Sorteerstation> stations = stationDao.getAlleStations();
            tabelStations.setItems(FXCollections.observableArrayList(stations));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon stations niet laden:\n" + e.getMessage());
        }
    }

    @FXML
    private void stationToevoegen() {
        Campus campus = cbCampus.getValue();
        String serienummer = tfSerienummer.getText().trim();
        String locatie = tfLocatie.getText().trim();
        String verdiepingTekst = tfVerdieping.getText().trim();
        LocalDate installatiedatum = dpInstallatiedatum.getValue();
        String firmware = tfFirmware.getText().trim();

        // Validatie
        if (campus == null) { WaarschuwingUtil.toonWaarschuwing("Invoer", "Selecteer een campus."); return; }
        if (serienummer.isEmpty()) { WaarschuwingUtil.toonWaarschuwing("Invoer", "Vul een serienummer in."); return; }
        if (locatie.isEmpty()) { WaarschuwingUtil.toonWaarschuwing("Invoer", "Vul een locatieomschrijving in."); return; }
        if (installatiedatum == null) { WaarschuwingUtil.toonWaarschuwing("Invoer", "Kies een installatiedatum."); return; }
        if (firmware.isEmpty()) { WaarschuwingUtil.toonWaarschuwing("Invoer", "Vul een firmwareversie in."); return; }

        Integer verdieping = null;
        if (!verdiepingTekst.isEmpty()) {
            try {
                verdieping = Integer.parseInt(verdiepingTekst);
            } catch (NumberFormatException e) {
                WaarschuwingUtil.toonWaarschuwing("Invoer", "Verdieping moet een geheel getal zijn."); return;
            }
        }

        try {
            stationDao.voegStationToe(campus.getCampusId(), serienummer, locatie,
                    verdieping, installatiedatum, firmware);
            laadStations();
            formulierLeegmaken();
            WaarschuwingUtil.toonInfo("Opgeslagen", "Station succesvol toegevoegd.");
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Station toevoegen mislukt:\n" + e.getMessage());
        }
    }

    @FXML
    private void statusWijzigen() {
        Sorteerstation geselecteerd = tabelStations.getSelectionModel().getSelectedItem();
        String nieuweStatus = cbNieuweStatus.getValue();

        if (geselecteerd == null) { WaarschuwingUtil.toonWaarschuwing("Selectie", "Selecteer eerst een station."); return; }
        if (nieuweStatus == null) { WaarschuwingUtil.toonWaarschuwing("Selectie", "Kies een nieuwe status."); return; }

        try {
            stationDao.wijzigStatus(geselecteerd.getStationId(), nieuweStatus);
            laadStations();
            WaarschuwingUtil.toonInfo("Opgeslagen", "Status gewijzigd naar '" + nieuweStatus + "'.");
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Status wijzigen mislukt:\n" + e.getMessage());
        }
    }

    private void formulierLeegmaken() {
        cbCampus.setValue(null);
        tfSerienummer.clear();
        tfLocatie.clear();
        tfVerdieping.clear();
        dpInstallatiedatum.setValue(null);
        tfFirmware.clear();
    }

    @FXML
    private void vernieuwen() {
        laadStations();
    }
}
