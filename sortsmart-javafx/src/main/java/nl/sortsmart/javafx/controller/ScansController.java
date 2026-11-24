package nl.sortsmart.javafx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import nl.sortsmart.javafx.dao.ScanDao;
import nl.sortsmart.javafx.dao.SorteerstationDao;
import nl.sortsmart.javafx.model.Scan;
import nl.sortsmart.javafx.model.Sorteerstation;
import nl.sortsmart.javafx.util.WaarschuwingUtil;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller voor het Scans-scherm: overzicht van de laatste scans
 * met filter op station en resultaat.
 */
public class ScansController implements Initializable {

    @FXML private TableView<Scan>             tabelScans;
    @FXML private TableColumn<Scan, Long>     kolScanId;
    @FXML private TableColumn<Scan, String>   kolStation;
    @FXML private TableColumn<Scan, String>   kolTijdstip;
    @FXML private TableColumn<Scan, String>   kolItemtype;
    @FXML private TableColumn<Scan, BigDecimal> kolBetrouwbaarheid;
    @FXML private TableColumn<Scan, String>   kolResultaat;

    @FXML private ComboBox<Sorteerstation>    cbStationFilter;
    @FXML private ComboBox<String>            cbResultaatFilter;
    @FXML private Spinner<Integer>            spLimiet;

    private final ScanDao           scanDao    = new ScanDao();
    private final SorteerstationDao stationDao = new SorteerstationDao();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        kolScanId.setCellValueFactory(new PropertyValueFactory<>("scanId"));
        kolStation.setCellValueFactory(new PropertyValueFactory<>("stationSerienummer"));
        kolBetrouwbaarheid.setCellValueFactory(new PropertyValueFactory<>("betrouwbaarheid"));
        kolResultaat.setCellValueFactory(new PropertyValueFactory<>("resultaat"));
        kolItemtype.setCellValueFactory(new PropertyValueFactory<>("itemtypeNaam"));

        // Betrouwbaarheid formatteren
        kolBetrouwbaarheid.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                } else {
                    setText(item.setScale(1, java.math.RoundingMode.HALF_UP) + "%");
                }
            }
        });

        // Itemtype formatteren
        kolItemtype.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("-");
                } else {
                    setText(item);
                }
            }
        });

        // Tijdstip formatteren
        kolTijdstip.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    Scan s = (Scan) getTableRow().getItem();
                    setText(s.getTijdstip() != null ? s.getTijdstip().format(FORMATTER) : "");
                }
            }
        });

        cbResultaatFilter.setItems(FXCollections.observableArrayList(
                "", "Gesorteerd", "MeerdereItems", "VloeistofGedetecteerd", "NietHerkend"));
        cbResultaatFilter.setValue("");

        spLimiet.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 500, 100, 10));

        laadStations();
        laadScans();
    }

    private void laadStations() {
        try {
            List<Sorteerstation> stations = stationDao.getAlleStations();
            cbStationFilter.setItems(FXCollections.observableArrayList(stations));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon stations niet laden:\n" + e.getMessage());
        }
    }

    private void laadScans() {
        try {
            Sorteerstation station = cbStationFilter.getValue();
            Integer stationId = station != null ? station.getStationId() : null;
            String resultaat = cbResultaatFilter.getValue();
            int limiet = spLimiet.getValue();
            List<Scan> scans = scanDao.getScans(stationId, resultaat, limiet);
            tabelScans.setItems(FXCollections.observableArrayList(scans));
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout", "Kon scans niet laden:\n" + e.getMessage());
        }
    }

    @FXML
    private void filterToepassen() {
        laadScans();
    }

    @FXML
    private void filterWissen() {
        cbStationFilter.setValue(null);
        cbResultaatFilter.setValue("");
        spLimiet.getValueFactory().setValue(100);
        laadScans();
    }

    @FXML
    private void vernieuwen() {
        laadScans();
    }
}
