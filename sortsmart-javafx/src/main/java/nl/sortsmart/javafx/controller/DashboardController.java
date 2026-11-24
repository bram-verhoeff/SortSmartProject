package nl.sortsmart.javafx.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import nl.sortsmart.javafx.dao.SorteerstationDao;
import nl.sortsmart.javafx.dao.VakDao;
import nl.sortsmart.javafx.model.Sorteerstation;
import nl.sortsmart.javafx.model.Vak;
import nl.sortsmart.javafx.util.WaarschuwingUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Dashboard: toont per sorteerstation de vakken met afvalfractie, kleur en vulgraad.
 * Vulgraad >= 80% wordt rood weergegeven.
 */
public class DashboardController implements Initializable {

    @FXML private VBox stationsContainer;

    private final SorteerstationDao stationDao = new SorteerstationDao();
    private final VakDao vakDao = new VakDao();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        laadDashboard();
    }

    @FXML
    private void vernieuwen() {
        stationsContainer.getChildren().clear();
        laadDashboard();
    }

    private void laadDashboard() {
        try {
            List<Sorteerstation> stations = stationDao.getAlleStations();
            for (Sorteerstation station : stations) {
                stationsContainer.getChildren().add(maakStationKaart(station));
            }
            if (stations.isEmpty()) {
                Label leeg = new Label("Geen sorteerstations gevonden.");
                leeg.getStyleClass().add("placeholder-label");
                stationsContainer.getChildren().add(leeg);
            }
        } catch (SQLException e) {
            WaarschuwingUtil.toonFout("Databasefout",
                    "Kon dashboard niet laden:\n" + e.getMessage());
        }
    }

    private VBox maakStationKaart(Sorteerstation station) {
        VBox kaart = new VBox(10);
        kaart.getStyleClass().add("station-card");
        kaart.setPadding(new Insets(14));

        // Koptekst
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titel = new Label(station.getSerienummer() + "  –  " + station.getLocatieOmschrijving());
        titel.getStyleClass().add("station-title");
        Label statusLabel = new Label(station.getStatus());
        statusLabel.getStyleClass().addAll("badge", badgeKlasseVoorStatus(station.getStatus()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label campus = new Label(station.getCampusNaam());
        campus.getStyleClass().add("campus-label");
        header.getChildren().addAll(titel, statusLabel, spacer, campus);

        // Vakken
        HBox vakkenRij = new HBox(14);
        vakkenRij.setPadding(new Insets(8, 0, 0, 0));
        try {
            List<Vak> vakken = vakDao.getVakkenVoorStation(station.getStationId());
            for (Vak vak : vakken) {
                vakkenRij.getChildren().add(maakVakKaart(vak));
            }
        } catch (SQLException e) {
            Label fout = new Label("Fout bij laden vakken.");
            fout.getStyleClass().add("error-label");
            vakkenRij.getChildren().add(fout);
        }

        kaart.getChildren().addAll(header, vakkenRij);
        return kaart;
    }

    private VBox maakVakKaart(Vak vak) {
        VBox vakBox = new VBox(6);
        vakBox.getStyleClass().add("vak-card");
        vakBox.setPadding(new Insets(10));
        vakBox.setPrefWidth(160);

        // Kleurblokje + fractienaam
        HBox fractieRij = new HBox(8);
        fractieRij.setAlignment(Pos.CENTER_LEFT);
        Region kleurBlok = new Region();
        kleurBlok.setPrefSize(14, 14);
        kleurBlok.setMinSize(14, 14);
        kleurBlok.setStyle("-fx-background-color: " + vak.getKleurcode() + "; -fx-background-radius: 3;");
        Label fractieLabel = new Label(vak.getFractieNaam());
        fractieLabel.getStyleClass().add("fractie-label");
        fractieRij.getChildren().addAll(kleurBlok, fractieLabel);

        // Vaknummer
        Label vakNrLabel = new Label("Vak " + vak.getVaknummer());
        vakNrLabel.getStyleClass().add("vak-nr-label");

        // Vulgraad balk
        double vulgraad = vak.getVulgraadProcent() / 100.0;
        ProgressBar balk = new ProgressBar(vulgraad);
        balk.setPrefWidth(140);
        balk.getStyleClass().add("vulgraad-bar");
        if (vak.getVulgraadProcent() >= 80) {
            balk.getStyleClass().add("vulgraad-rood");
        }

        Label procent = new Label(vak.getVulgraadProcent() + "%");
        procent.getStyleClass().add(vak.getVulgraadProcent() >= 80 ? "procent-rood" : "procent-label");

        vakBox.getChildren().addAll(vakNrLabel, fractieRij, balk, procent);
        return vakBox;
    }

    private String badgeKlasseVoorStatus(String status) {
        return switch (status) {
            case "Actief"    -> "badge-actief";
            case "Onderhoud" -> "badge-onderhoud";
            case "Storing"   -> "badge-storing";
            default          -> "badge-inactief";
        };
    }
}
