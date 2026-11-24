package nl.sortsmart.javafx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import nl.sortsmart.javafx.dao.MeldingDao;
import nl.sortsmart.javafx.dao.MedewerkerDao;
import nl.sortsmart.javafx.model.Melding;
import nl.sortsmart.javafx.model.Medewerker;
import nl.sortsmart.javafx.util.WaarschuwingUtil;

import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller voor Meldingen: open meldingen tonen en afhandelen.
 * Bedrijfsregel: VakVol-meldingen hebben altijd een vaknummer.
 */
public class MeldingenController implements Initializable {

    @FXML private TableView<Melding> tabelMeldingen;
    @FXML private TableColumn<Melding, Integer>  kolId;
    @FXML private TableColumn<Melding, String>   kolStation;
    @FXML private TableColumn<Melding, String>   kolType;
    @FXML private TableColumn<Melding, Integer>  kolVak;
    @FXML private TableColumn<Melding, String>   kolOmschrijving;
    @FXML private TableColumn<Melding, String>   kolGemeld;
    @FXML private TableColumn<Melding, String>   kolStatus;

    @FXML private ComboBox<Medewerker> cbMedewerker;
    @FXML private CheckBox cbToonAlles;

    private final MeldingDao meldingDao   = new MeldingDao();
    private final MedewerkerDao medewerkerDao = new MedewerkerDao();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        kolId.setCellValueFactory(new PropertyValueFactory<>("meldingId"));
        kolStation.setCellValueFactory(new PropertyValueFactory<>("stationSerienummer"));
        kolType.setCellValueFactory(new PropertyValueFactory<>("type"));
        kolVak.setCellValueFactory(new PropertyValueFactory<>("vaknummer"));
        kolOmschrijving.setCellValueFactory(new PropertyValueFactory<>("omschrijving"));
        kolStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Tijdstip formatteren
        kolGemeld.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    Melding m = (Melding) getTableRow().getItem();
                    setText(m.getTijdstipGemeld() != null ? m.getTijdstipGemeld().format(FORMATTER) : "");
                }
            }
        });

        laadMedewerkers();
        laadMeldingen();
    }

    private void laadMedewerkers() {
        try {
            List<Medewerker> medewerkers = medewerkerDao.getAlleMedewerkers();
            cbMedewerker.setItems(FXCollections.observableArrayList(medewerkers));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon medewerkers niet laden:\n" + e.getMessage());
        }
    }

    private void laadMeldingen() {
        try {
            List<Melding> meldingen = cbToonAlles.isSelected()
                    ? meldingDao.getAlleMeldingen()
                    : meldingDao.getOpenMeldingen();
            tabelMeldingen.setItems(FXCollections.observableArrayList(meldingen));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon meldingen niet laden:\n" + e.getMessage());
        }
    }

    @FXML
    private void handelAf() {
        Melding geselecteerd = tabelMeldingen.getSelectionModel().getSelectedItem();
        Medewerker medewerker = cbMedewerker.getValue();

        if (geselecteerd == null) {
            WaarschuwingUtil.toonWaarschuwing("Selectie", "Selecteer eerst een melding.");
            return;
        }
        if ("Afgehandeld".equals(geselecteerd.getStatus())) {
            WaarschuwingUtil.toonWaarschuwing("Al afgehandeld", "Deze melding is al afgehandeld.");
            return;
        }
        if (medewerker == null) {
            WaarschuwingUtil.toonWaarschuwing("Medewerker", "Selecteer de medewerker die afhandelt.");
            return;
        }

        try {
            meldingDao.handelAf(geselecteerd.getMeldingId(), medewerker.getMedewerkerId());
            laadMeldingen();
            WaarschuwingUtil.toonInfo("Afgehandeld", "Melding #" + geselecteerd.getMeldingId() + " is afgehandeld.");
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Afhandelen mislukt:\n" + e.getMessage());
        }
    }

    @FXML
    private void filterWijzigen() {
        laadMeldingen();
    }

    @FXML
    private void vernieuwen() {
        laadMeldingen();
    }
}
