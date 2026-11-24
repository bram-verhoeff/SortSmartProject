package nl.sortsmart.javafx.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller voor het hoofdvenster (navigatie + inhoudsgebied).
 */
public class MainController implements Initializable {

    @FXML private StackPane contentArea;

    private Object huidigeController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Laad het Stationsdisplay als standaardscherm bij opstarten
        toonStationsdisplay();
    }

    @FXML
    private void toonStationsdisplay() {
        laadScherm("StationsdisplayView.fxml");
    }

    @FXML
    private void toonDashboard() {
        laadScherm("DashboardView.fxml");
    }

    @FXML
    private void toonStations() {
        laadScherm("StationsView.fxml");
    }

    @FXML
    private void toonMeldingen() {
        laadScherm("MeldingenView.fxml");
    }

    @FXML
    private void toonLeging() {
        laadScherm("LegingView.fxml");
    }

    @FXML
    private void toonScans() {
        laadScherm("ScansView.fxml");
    }

    @FXML
    private void toonInstellingen() {
        laadScherm("InstellingenView.fxml");
    }

    private void laadScherm(String fxmlBestand) {
        try {
            // Als het vorige scherm de camera gebruikte, sluiten we die netjes af
            if (huidigeController instanceof StationsdisplayController displayController) {
                displayController.onVerbergScherm();
            }

            var loader = nl.sortsmart.javafx.util.FxmlLader.maakLader(fxmlBestand);
            var view = (javafx.scene.layout.Pane) loader.load();
            huidigeController = loader.getController();

            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            nl.sortsmart.javafx.util.WaarschuwingUtil.toonFout(
                    "Scherm laden mislukt",
                    "Het scherm kon niet worden geladen: " + fxmlBestand + "\n" + e.getMessage()
            );
        }
    }
}
