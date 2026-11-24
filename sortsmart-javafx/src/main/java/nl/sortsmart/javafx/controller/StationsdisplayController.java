package nl.sortsmart.javafx.controller;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import nl.sortsmart.javafx.dao.AfvalfractieDao;
import nl.sortsmart.javafx.dao.ScanDao;
import nl.sortsmart.javafx.dao.SorteerstationDao;
import nl.sortsmart.javafx.herkenning.CameraManager;
import nl.sortsmart.javafx.herkenning.HerkenningConfig;
import nl.sortsmart.javafx.herkenning.HerkenningResultaat;
import nl.sortsmart.javafx.herkenning.OllamaProvider;
import nl.sortsmart.javafx.model.Afvalfractie;
import nl.sortsmart.javafx.model.Sorteerstation;
import nl.sortsmart.javafx.service.DisplayToestand;
import nl.sortsmart.javafx.service.StationDisplayService;
import nl.sortsmart.javafx.util.WaarschuwingUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller voor het interactieve Stationsdisplay (Mockups 1 t/m 6).
 * Bevat live camera-preview via MSMF, bewegingsdetectie en Ollama AI-herkenning.
 */
public class StationsdisplayController implements Initializable {

    // Hoofdstructuur
    @FXML private VBox displayRoot;
    @FXML private HBox headerBar;
    @FXML private HBox statusPill;
    @FXML private Circle statusDot;
    @FXML private Label statusLabel;

    // Centraal paneel
    @FXML private StackPane circleContainer;
    @FXML private SVGPath circleIcon;
    @FXML private VBox textContainer;
    @FXML private Label tagLabel;
    @FXML private Label lblHerkentijd;
    @FXML private Label headingLabel;
    @FXML private Label descriptionLabel;
    @FXML private ProgressBar scanProgressBar;

    // Camera preview & handmatige bediening
    @FXML private StackPane cameraContainer;
    @FXML private ImageView cameraPreviewView;
    @FXML private VBox cameraFallbackBox;
    @FXML private Label cameraStatusLabel;
    @FXML private Button btnNuScannen;
    @FXML private Button btnFotoKiezen;
    @FXML private Button btnAnnuleren;

    // Ondervakken
    @FXML private HBox fractieVakkenContainer;
    @FXML private HBox geluktBalkContainer;
    @FXML private Label geluktBalkTitel;
    @FXML private Label geluktBalkSub;

    // Simulatiebalk
    @FXML private TitledPane simTitledPane;
    @FXML private ComboBox<Sorteerstation> simStationCombo;
    @FXML private HBox simKnoppenBox;

    private final StationDisplayService displayService = new StationDisplayService();
    private final AfvalfractieDao fractieDao = new AfvalfractieDao();
    private final SorteerstationDao stationDao = new SorteerstationDao();
    private final ScanDao scanDao = new ScanDao();

    private final HerkenningConfig config = HerkenningConfig.getInstantie();
    private final CameraManager cameraManager = new CameraManager(config);
    private final OllamaProvider ollamaProvider = new OllamaProvider(config);
    private final AtomicBoolean scanBezig = new AtomicBoolean(false);
    private final AtomicBoolean scanGeannuleerd = new AtomicBoolean(false);
    private Timeline scanTellerTimeline;
    private int verstrekenSeconden = 0;

    private List<Afvalfractie> alleFracties = new ArrayList<>();
    private Timeline progressTimeline;

    // SVG Icon definities
    private static final String SVG_DROP = "M12 3v10m0 0l-4-4m4 4l4-4 M6 17h12 M8 21h8";
    private static final String SVG_SCAN = "M4 8V6a2 2 0 012-2h2 M16 4h2a2 2 0 012 2v2 M20 16v2a2 2 0 01-2 2h-2 M8 20H6a2 2 0 01-2-2v-2 M3 12h18";
    private static final String SVG_PMD = "M10 2h4v2h-4z M9 4h6a2 2 0 012 2v1h-10V6a2 2 0 012-2z M7 7h10v13a2 2 0 01-2 2H9a2 2 0 01-2-2V7z M7 12h10 M7 16h10";
    private static final String SVG_PAPIER = "M7 3h8l4 4v12a2 2 0 01-2 2H7a2 2 0 01-2-2V5a2 2 0 012-2z M14 3v5h5 M9 12h6 M9 16h4";
    private static final String SVG_REST = "M4 7h16 M10 11v6 M14 11v6 M5 7l1 12a2 2 0 002 2h8a2 2 0 002-2l1-12 M9 7V4a1 1 0 011-1h4a1 1 0 011 1v3";
    private static final String SVG_ETEN = "M12 3c1 2 0 3-1 4 M12 7c-4 0-6 3-6 7 0 5 3 7 6 7s6-2 6-7c0-4-2-7-6-7z";
    private static final String SVG_CHECK = "M5 13l4 4L19 7";
    private static final String SVG_MULTI = "M3 8h6a1 1 0 011 1v10a1 1 0 01-1 1H3a1 1 0 01-1-1V9a1 1 0 011-1z M11 6h6a1 1 0 011 1v12a1 1 0 01-1 1h-6a1 1 0 01-1-1V7a1 1 0 011-1z M4 4l1 1.5 M7 3v2 M1 5.5l1.5.5";
    private static final String SVG_VLOEISTOF = "M12 2.7l5.7 5.7a8 8 0 11-11.4 0L12 2.7z M12 8a4 4 0 00-4 4";
    private static final String SVG_QUESTION = "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        laadData();
        setupListeners();
        updateScherm(displayService.getToestand(), displayService.getActieveFractie());
        startCamera();

        // Warm actieve herkenningsprovider op de achtergrond op
        CompletableFuture.runAsync(nl.sortsmart.javafx.herkenning.AiProviderManager::warmUpActieveProvider);
    }

    /**
     * Wordt aangeroepen wanneer de gebruiker naar een ander scherm navigeert.
     * Sluit de camera netjes af om systeembronnen te sparen.
     */
    public void onVerbergScherm() {
        stopScanTeller();
        cameraManager.stop();
        if (cameraStatusLabel != null) {
            cameraStatusLabel.setText("Camera: Inactief");
        }
    }

    private void startCamera() {
        cameraStatusLabel.setText("Camera: Verbinden...");
        cameraManager.start(
                // Frame listener voor de live preview
                image -> {
                    cameraPreviewView.setImage(image);
                    if (cameraFallbackBox.isVisible()) {
                        cameraFallbackBox.setVisible(false);
                    }
                    cameraStatusLabel.setText("Camera: Live (MSMF)");
                    cameraStatusLabel.setStyle("-fx-text-fill: #10B981;");
                },
                // Trigger listener bij automatische bewegingsdetectie (item ligt stil)
                snapshotJpegBytes -> {
                    if (!scanBezig.get() && displayService.getToestand() == DisplayToestand.KLAAR) {
                        voerAiScanUit(snapshotJpegBytes);
                    }
                }
        );

        // Geef camera 800ms om op te starten; toon anders nette fallback
        Platform.runLater(() -> {
            new Timeline(new KeyFrame(Duration.millis(1200), e -> {
                if (!cameraManager.isCameraBeschikbaar()) {
                    cameraFallbackBox.setVisible(true);
                    cameraStatusLabel.setText("Camera: Niet verbonden (kies foto)");
                    cameraStatusLabel.setStyle("-fx-text-fill: #9CA3AF;");
                }
            })).play();
        });
    }

    @FXML
    private void nuScannen() {
        if (scanBezig.get()) return;

        byte[] liveBytes = cameraManager.getLaatsteJpegBytes();
        if (liveBytes != null && liveBytes.length > 0) {
            voerAiScanUit(liveBytes);
        } else {
            // Als er geen actieve camerastream is, vraag de gebruiker een foto te kiezen
            fotoKiezen();
        }
    }

    @FXML
    private void fotoKiezen() {
        if (scanBezig.get()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Kies een afvalfoto voor herkenning");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Afbeeldingen (*.jpg, *.jpeg, *.png, *.webp)", "*.jpg", "*.jpeg", "*.png", "*.webp"),
                new FileChooser.ExtensionFilter("Alle bestanden", "*.*")
        );

        File file = chooser.showOpenDialog(displayRoot.getScene().getWindow());
        if (file != null) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                // Toon gekozen foto in preview
                Image img = new Image(new ByteArrayInputStream(bytes));
                cameraPreviewView.setImage(img);
                cameraFallbackBox.setVisible(false);
                cameraStatusLabel.setText("Foto geladen: " + file.getName());
                cameraStatusLabel.setStyle("-fx-text-fill: #60A5FA;");

                voerAiScanUit(bytes);
            } catch (Exception e) {
                WaarschuwingUtil.toonFout("Bestandsfout", "Kon foto niet inlezen: " + e.getMessage());
            }
        }
    }

    @FXML
    private void annuleerScan() {
        scanGeannuleerd.set(true);
        stopScanTeller();
        displayService.resetNaarKlaar();
        scanBezig.set(false);
        cameraManager.resetBeweegDetectie();
    }

    private void startScanTeller() {
        verstrekenSeconden = 0;
        headingLabel.setText("Even kijken... 0 s");
        if (btnNuScannen != null) {
            btnNuScannen.setVisible(false);
            btnNuScannen.setManaged(false);
        }
        if (btnFotoKiezen != null) {
            btnFotoKiezen.setVisible(false);
            btnFotoKiezen.setManaged(false);
        }
        if (btnAnnuleren != null) {
            btnAnnuleren.setVisible(true);
            btnAnnuleren.setManaged(true);
        }

        if (scanTellerTimeline != null) {
            scanTellerTimeline.stop();
        }
        scanTellerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            verstrekenSeconden++;
            headingLabel.setText("Even kijken... " + verstrekenSeconden + " s");
        }));
        scanTellerTimeline.setCycleCount(Timeline.INDEFINITE);
        scanTellerTimeline.play();
    }

    private void stopScanTeller() {
        if (scanTellerTimeline != null) {
            scanTellerTimeline.stop();
            scanTellerTimeline = null;
        }
        if (btnAnnuleren != null) {
            btnAnnuleren.setVisible(false);
            btnAnnuleren.setManaged(false);
        }
        if (btnNuScannen != null) {
            btnNuScannen.setVisible(true);
            btnNuScannen.setManaged(true);
        }
        if (btnFotoKiezen != null) {
            btnFotoKiezen.setVisible(true);
            btnFotoKiezen.setManaged(true);
        }
    }

    private void voerAiScanUit(byte[] jpegBytes) {
        if (scanBezig.getAndSet(true)) {
            return;
        }
        scanGeannuleerd.set(false);

        // Stap 1: Toon scannen status & start lopende teller
        displayService.toestandProperty().set(DisplayToestand.SCANNEN);
        startScanTeller();

        nl.sortsmart.javafx.herkenning.AiProvider provider = nl.sortsmart.javafx.herkenning.AiProviderManager.getActieveProvider();
        int minConfidence = nl.sortsmart.javafx.herkenning.AiProviderManager.getActieveMinConfidence();

        CompletableFuture.supplyAsync(() -> {
            try {
                return provider.herkenAfval(jpegBytes, minConfidence);
            } catch (Exception e) {
                System.err.println("[AI-Herkenning] Fout bij classificatie: " + e.getMessage());
                return HerkenningResultaat.nietHerkend("Herkenning mislukt: " + e.getMessage());
            }
        }).thenAccept(resultaat -> Platform.runLater(() -> {
            stopScanTeller();
            if (scanGeannuleerd.get()) {
                return;
            }
            verwerkAiResultaat(resultaat);
        }));
    }

    private void verwerkAiResultaat(HerkenningResultaat res) {
        if (lblHerkentijd != null) {
            if (res.getLatencyMs() > 0) {
                lblHerkentijd.setText(String.format("⚡ %d ms (%s)", res.getLatencyMs(), config.isClipModus() ? "CLIP" : "Ollama"));
                lblHerkentijd.setVisible(true);
                lblHerkentijd.setManaged(true);
            } else {
                lblHerkentijd.setVisible(false);
                lblHerkentijd.setManaged(false);
            }
        }

        if (res.isVloeistof()) {
            displayService.startVloeistofGedetecteerd();
            geefScanVrijNaDuur(4500);
            return;
        }

        if (res.isMeerdereItems()) {
            displayService.startMeerdereItems();
            geefScanVrijNaDuur(4500);
            return;
        }

        if ("Gesorteerd".equalsIgnoreCase(res.getResultaatType()) && res.getFractie() != null) {
            Afvalfractie gevondenFractie = vindFractieOpNaam(res.getFractie());
            if (gevondenFractie != null) {
                Integer itemTypeId = null;
                try {
                    itemTypeId = scanDao.zoekOfKoppelItemtype(res.getItemtype(), gevondenFractie.getFractieId());
                } catch (SQLException ignored) {}

                displayService.startScanVoorFractie(
                        gevondenFractie,
                        itemTypeId,
                        BigDecimal.valueOf(res.getBetrouwbaarheid())
                );
                geefScanVrijNaDuur(5600);
                return;
            }
        }

        // Niet herkend of te lage betrouwbaarheid
        displayService.startNietHerkend(res.getUitleg());
        geefScanVrijNaDuur(4500);
    }

    private void geefScanVrijNaDuur(long millis) {
        new Timeline(new KeyFrame(Duration.millis(millis), e -> {
            scanBezig.set(false);
            cameraManager.resetBeweegDetectie();
        })).play();
    }

    private Afvalfractie vindFractieOpNaam(String naam) {
        if (naam == null) return null;
        for (Afvalfractie f : alleFracties) {
            if (f.getNaam().equalsIgnoreCase(naam)) {
                return f;
            }
        }
        // Match op gedeeltelijke naam (bv. "Rest" vs "Restafval")
        for (Afvalfractie f : alleFracties) {
            if (f.getNaam().toLowerCase().contains(naam.toLowerCase()) ||
                    naam.toLowerCase().contains(f.getNaam().toLowerCase())) {
                return f;
            }
        }
        return alleFracties.isEmpty() ? null : alleFracties.get(0);
    }

    private void laadData() {
        try {
            alleFracties = fractieDao.getAlleFracties();
            bouwOndervakken();
            bouwSimulatieKnoppen();

            List<Sorteerstation> stations = stationDao.getAlleStations();
            simStationCombo.getItems().setAll(stations);
            if (!stations.isEmpty()) {
                simStationCombo.getSelectionModel().selectFirst();
                displayService.setActiefStationId(stations.get(0).getStationId());
            }
            simStationCombo.valueProperty().addListener((obs, oud, nieuw) -> {
                if (nieuw != null) {
                    displayService.setActiefStationId(nieuw.getStationId());
                }
            });
        } catch (SQLException e) {
            System.err.println("[Stationsdisplay] Database niet gereed: " + e.getMessage());
            // Fallback met standaard fracties
            alleFracties = List.of(
                    new Afvalfractie(1, "Papier", "#1E63B5", "Papier en karton"),
                    new Afvalfractie(3, "Restafval", "#5A5A5A", "Restafval"),
                    new Afvalfractie(2, "PMD", "#F28C00", "Plastic, blik en drankkartons"),
                    new Afvalfractie(4, "Etensresten", "#3C8D2F", "GFT en etensresten")
            );
            bouwOndervakken();
            bouwSimulatieKnoppen();
        }
    }

    private void setupListeners() {
        displayService.toestandProperty().addListener((obs, oud, nieuw) -> {
            Platform.runLater(() -> updateScherm(nieuw, displayService.getActieveFractie()));
        });

        displayService.actieveFractieProperty().addListener((obs, oud, nieuw) -> {
            Platform.runLater(() -> updateScherm(displayService.getToestand(), nieuw));
        });
    }

    private void updateScherm(DisplayToestand toestand, Afvalfractie fractie) {
        // Reset root style
        displayRoot.getStyleClass().removeAll("display-root-normal", "display-root-gelukt");
        geluktBalkContainer.setVisible(false);
        geluktBalkContainer.setManaged(false);
        fractieVakkenContainer.setVisible(true);
        fractieVakkenContainer.setManaged(true);
        scanProgressBar.setVisible(false);
        scanProgressBar.setManaged(false);
        tagLabel.setVisible(false);
        tagLabel.setManaged(false);

        stopProgressAnimatie();
        if (toestand != DisplayToestand.SCANNEN) {
            stopScanTeller();
        }

        switch (toestand) {
            case KLAAR -> toestandKlaar();
            case SCANNEN -> toestandScannen();
            case HERKEND -> toestandHerkend(fractie);
            case GELUKT -> toestandGelukt(fractie);
            case MEERDERE_ITEMS -> toestandMeerdereItems();
            case VLOEISTOF -> toestandVloeistof();
            case NIET_HERKEND -> toestandNietHerkend();
        }

        // Update highlight van de 4 vakken onderaan
        updateVakkenHighlight(toestand, fractie);
    }

    // --- Mockup 1: Klaar ---
    private void toestandKlaar() {
        displayRoot.getStyleClass().add("display-root-normal");
        if (lblHerkentijd != null) {
            lblHerkentijd.setVisible(false);
            lblHerkentijd.setManaged(false);
        }

        statusDot.setStyle("-fx-fill: #10B981;"); // Groene stip
        statusLabel.setText("Klaar");

        circleContainer.setStyle("-fx-background-color: #1F2937; -fx-border-style: dashed; -fx-border-color: #374151; -fx-border-width: 2; -fx-border-radius: 100; -fx-background-radius: 100;");
        circleIcon.setContent(SVG_DROP);
        circleIcon.setScaleX(2.8);
        circleIcon.setScaleY(2.8);
        circleIcon.setStyle("-fx-stroke: #9CA3AF; -fx-fill: transparent; -fx-stroke-width: 2; -fx-stroke-linecap: round;");

        headingLabel.setText("Gooi je afval in de bak");
        headingLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 34px; -fx-font-weight: 800;");
        descriptionLabel.setText("Eén item tegelijk. SortSmart sorteert het daarna zelf in de juiste bak.");
    }

    // --- Mockup 2: Scannen ---
    private void toestandScannen() {
        displayRoot.getStyleClass().add("display-root-normal");

        statusDot.setStyle("-fx-fill: #3B82F6;"); // Blauwe stip
        statusLabel.setText("Scannen");

        circleContainer.setStyle("-fx-background-color: #1E293B; -fx-border-color: #3B82F6; -fx-border-width: 3; -fx-border-radius: 100; -fx-background-radius: 100;");
        circleIcon.setContent(SVG_SCAN);
        circleIcon.setScaleX(2.8);
        circleIcon.setScaleY(2.8);
        circleIcon.setStyle("-fx-stroke: #60A5FA; -fx-fill: transparent; -fx-stroke-width: 2; -fx-stroke-linecap: round;");

        headingLabel.setText("Even kijken...");
        headingLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 34px; -fx-font-weight: 800;");
        descriptionLabel.setText("Laat het item even stil in het bakje liggen.");

        scanProgressBar.setVisible(true);
        scanProgressBar.setManaged(true);
        startProgressAnimatie();
    }

    // --- Mockup 3: Herkend ---
    private void toestandHerkend(Afvalfractie fractie) {
        displayRoot.getStyleClass().add("display-root-normal");

        String kleur = fractie != null ? fractie.getKleurcode() : "#F28C00";
        String fractieNaam = fractie != null ? fractie.getNaam() : "PMD";

        statusDot.setStyle("-fx-fill: " + kleur + ";");
        statusLabel.setText("Bezig met sorteren");

        circleContainer.setStyle("-fx-background-color: #1F2937; -fx-border-color: " + kleur + "; -fx-border-width: 3; -fx-border-radius: 100; -fx-background-radius: 100;");
        circleIcon.setContent(bepaalSvgVoorFractie(fractieNaam));
        circleIcon.setScaleX(2.8);
        circleIcon.setScaleY(2.8);
        circleIcon.setStyle("-fx-stroke: " + kleur + "; -fx-fill: transparent; -fx-stroke-width: 2; -fx-stroke-linecap: round;");

        tagLabel.setVisible(true);
        tagLabel.setManaged(true);
        tagLabel.setText("HERKEND");
        tagLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 13px; -fx-font-weight: 700;");

        headingLabel.setText("Dit is " + fractieNaam);
        headingLabel.setStyle("-fx-text-fill: " + kleur + "; -fx-font-size: 36px; -fx-font-weight: 800;");
        descriptionLabel.setText("Het bakje kantelt nu naar het " + fractieNaam + "-vak.");
    }

    // --- Mockup 4: Gelukt! ---
    private void toestandGelukt(Afvalfractie fractie) {
        displayRoot.getStyleClass().add("display-root-gelukt"); // Volledig groene achtergrond

        statusDot.setStyle("-fx-fill: #047857;");
        statusLabel.setText("Bakje terug in rust");
        statusPill.setStyle("-fx-background-color: #065F46; -fx-background-radius: 20; -fx-padding: 6 16 6 12;");

        circleContainer.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 100; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 2);");
        circleIcon.setContent(SVG_CHECK);
        circleIcon.setScaleX(3.0);
        circleIcon.setScaleY(3.0);
        circleIcon.setStyle("-fx-stroke: #15803D; -fx-fill: transparent; -fx-stroke-width: 3.5; -fx-stroke-linecap: round; -fx-stroke-linejoin: round;");

        String fractieNaam = fractie != null ? fractie.getNaam() : "PMD";
        headingLabel.setText("Gelukt!");
        headingLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 38px; -fx-font-weight: 800;");
        descriptionLabel.setText(fractieNaam + " is goed gescheiden.\nDankzij jou kan deze bak gerecycled worden.");
        descriptionLabel.setStyle("-fx-text-fill: #ECFDF5; -fx-font-size: 18px;");

        fractieVakkenContainer.setVisible(false);
        fractieVakkenContainer.setManaged(false);

        geluktBalkContainer.setVisible(true);
        geluktBalkContainer.setManaged(true);
    }

    // --- Mockup 5: Meerdere items ---
    private void toestandMeerdereItems() {
        displayRoot.getStyleClass().add("display-root-normal");

        statusDot.setStyle("-fx-fill: #EAB308;");
        statusLabel.setText("Let op");

        circleContainer.setStyle("-fx-background-color: #1F2937; -fx-border-color: #EAB308; -fx-border-width: 3; -fx-border-radius: 100; -fx-background-radius: 100;");
        circleIcon.setContent(SVG_MULTI);
        circleIcon.setScaleX(2.8);
        circleIcon.setScaleY(2.8);
        circleIcon.setStyle("-fx-stroke: #FACC15; -fx-fill: transparent; -fx-stroke-width: 2; -fx-stroke-linecap: round;");

        headingLabel.setText("Eén item tegelijk, graag");
        headingLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 34px; -fx-font-weight: 800;");
        descriptionLabel.setText("We zien meerdere items. Neem je afval terug en leg ze één voor één in het bakje.");
    }

    // --- Mockup 6: Vloeistof ---
    private void toestandVloeistof() {
        displayRoot.getStyleClass().add("display-root-normal");

        statusDot.setStyle("-fx-fill: #EAB308;");
        statusLabel.setText("Let op");

        circleContainer.setStyle("-fx-background-color: #1F2937; -fx-border-color: #EAB308; -fx-border-width: 3; -fx-border-radius: 100; -fx-background-radius: 100;");
        circleIcon.setContent(SVG_VLOEISTOF);
        circleIcon.setScaleX(2.8);
        circleIcon.setScaleY(2.8);
        circleIcon.setStyle("-fx-stroke: #FACC15; -fx-fill: transparent; -fx-stroke-width: 2; -fx-stroke-linecap: round;");

        headingLabel.setText("Eerst even leegmaken");
        headingLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 34px; -fx-font-weight: 800;");
        descriptionLabel.setText("Er zit nog vloeistof in je verpakking. Giet het leeg bij de gootsteen en scan opnieuw.");
    }

    // --- Niet herkend ---
    private void toestandNietHerkend() {
        displayRoot.getStyleClass().add("display-root-normal");

        statusDot.setStyle("-fx-fill: #F59E0B;");
        statusLabel.setText("Niet herkend");

        circleContainer.setStyle("-fx-background-color: #1F2937; -fx-border-color: #F59E0B; -fx-border-width: 3; -fx-border-radius: 100; -fx-background-radius: 100;");
        circleIcon.setContent(SVG_QUESTION);
        circleIcon.setScaleX(2.6);
        circleIcon.setScaleY(2.6);
        circleIcon.setStyle("-fx-stroke: #FBBF24; -fx-fill: transparent; -fx-stroke-width: 2; -fx-stroke-linecap: round;");

        headingLabel.setText("Afval niet herkend");
        headingLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 34px; -fx-font-weight: 800;");
        descriptionLabel.setText("Leg het item opnieuw in het bakje of deponeer het bij het restafval.");
    }

    private void startProgressAnimatie() {
        scanProgressBar.setProgress(0);
        progressTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(scanProgressBar.progressProperty(), 0.05)),
                new KeyFrame(Duration.millis(900), new KeyValue(scanProgressBar.progressProperty(), 0.95))
        );
        progressTimeline.setAutoReverse(true);
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        progressTimeline.play();
    }

    private void stopProgressAnimatie() {
        if (progressTimeline != null) {
            progressTimeline.stop();
            progressTimeline = null;
        }
    }

    private String bepaalSvgVoorFractie(String naam) {
        String n = naam.toLowerCase();
        if (n.contains("pmd")) return SVG_PMD;
        if (n.contains("papier")) return SVG_PAPIER;
        if (n.contains("eten") || n.contains("gft")) return SVG_ETEN;
        return SVG_REST;
    }

    private void bouwOndervakken() {
        fractieVakkenContainer.getChildren().clear();
        for (Afvalfractie fractie : alleFracties) {
            HBox vakBox = new HBox(10);
            vakBox.setAlignment(Pos.CENTER);
            vakBox.setPrefHeight(56);
            HBox.setHgrow(vakBox, Priority.ALWAYS);
            vakBox.getStyleClass().add("display-vak-box");
            vakBox.setUserData(fractie);

            Circle stip = new Circle(5);
            stip.setStyle("-fx-fill: " + fractie.getKleurcode() + ";");

            Label naamLabel = new Label(fractie.getNaam().equalsIgnoreCase("Rest") ? "Restafval" : fractie.getNaam());
            naamLabel.getStyleClass().add("display-vak-naam");

            vakBox.getChildren().addAll(stip, naamLabel);
            fractieVakkenContainer.getChildren().add(vakBox);
        }
    }

    private void updateVakkenHighlight(DisplayToestand toestand, Afvalfractie actief) {
        for (var node : fractieVakkenContainer.getChildren()) {
            if (node instanceof HBox vakBox) {
                Afvalfractie f = (Afvalfractie) vakBox.getUserData();
                vakBox.getStyleClass().removeAll("display-vak-box-active");

                // Verwijder eventuele 'Hierheen' tekst
                Label naamLabel = (Label) vakBox.getChildren().get(1);
                String basisNaam = f.getNaam().equalsIgnoreCase("Rest") ? "Restafval" : f.getNaam();

                if (toestand == DisplayToestand.HERKEND && actief != null && f.getFractieId() == actief.getFractieId()) {
                    vakBox.setStyle("-fx-background-color: #1E293B; -fx-border-color: " + f.getKleurcode() + "; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12;");
                    naamLabel.setText(basisNaam + " ↓ Hierheen");
                    naamLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: 800;");
                } else {
                    vakBox.setStyle("-fx-background-color: #1E293B; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12;");
                    naamLabel.setText(basisNaam);
                    naamLabel.setStyle("-fx-text-fill: #E5E7EB; -fx-font-weight: 700;");
                }
            }
        }
    }

    private void bouwSimulatieKnoppen() {
        simKnoppenBox.getChildren().clear();

        // Knop per fractie
        for (Afvalfractie fractie : alleFracties) {
            Button btn = new Button("Scan " + fractie.getNaam());
            btn.getStyleClass().add("sim-btn");
            btn.setStyle("-fx-border-color: " + fractie.getKleurcode() + "; -fx-border-width: 1.5; -fx-text-fill: #FFFFFF;");
            btn.setOnAction(e -> displayService.startScanVoorFractie(fractie));
            simKnoppenBox.getChildren().add(btn);
        }

        // Knop Meerdere Items
        Button btnMulti = new Button("⚠️ Meerdere items");
        btnMulti.getStyleClass().addAll("sim-btn", "sim-btn-warning");
        btnMulti.setOnAction(e -> displayService.startMeerdereItems());

        // Knop Vloeistof
        Button btnVloeistof = new Button("💧 Vloeistof");
        btnVloeistof.getStyleClass().addAll("sim-btn", "sim-btn-warning");
        btnVloeistof.setOnAction(e -> displayService.startVloeistofGedetecteerd());

        // Knop Reset
        Button btnReset = new Button("↺ Reset (Klaar)");
        btnReset.getStyleClass().addAll("sim-btn", "sim-btn-secondary");
        btnReset.setOnAction(e -> displayService.resetNaarKlaar());

        simKnoppenBox.getChildren().addAll(btnMulti, btnVloeistof, btnReset);
    }
}
