package nl.sortsmart.javafx.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import nl.sortsmart.javafx.herkenning.AiProviderManager;
import nl.sortsmart.javafx.herkenning.HerkenningConfig;
import nl.sortsmart.javafx.herkenning.OllamaProvider;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller voor het Instellingen-scherm.
 * Configureert herkenningsmodus (CLIP / Ollama), modelpaden, betrouwbaarheid en camera.
 */
public class InstellingenController implements Initializable {

    // Modus
    @FXML private ComboBox<String> cbProviderModus;
    @FXML private Label            lblModusUitleg;
    @FXML private VBox             kaartClip;
    @FXML private VBox             kaartOllama;

    // CLIP
    @FXML private TextField txtClipModelPad;
    @FXML private Label     lblClipStatus;
    @FXML private Slider    sliderClipConfidence;
    @FXML private Label     lblClipConfidence;

    // Ollama
    @FXML private TextField txtOllamaUrl;
    @FXML private Label     lblVerbindingStatus;
    @FXML private ComboBox<String> cbModel;
    @FXML private Slider    sliderConfidence;
    @FXML private Label     lblConfidence;
    @FXML private Spinner<Integer> spTimeout;

    // Camera
    @FXML private ComboBox<String> cbCamera;
    @FXML private CheckBox  chkAutoScan;
    @FXML private Label     lblOpslaanStatus;

    private final HerkenningConfig config = HerkenningConfig.getInstantie();
    private OllamaProvider ollamaProvider = new OllamaProvider(config);

    private static final String MODUS_CLIP = "Snel (CLIP, lokaal)";
    private static final String MODUS_OLLAMA = "Nauwkeurig (Ollama)";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Herkenningsmodus
        cbProviderModus.setItems(FXCollections.observableArrayList(MODUS_CLIP, MODUS_OLLAMA));
        cbProviderModus.setValue(config.isClipModus() ? MODUS_CLIP : MODUS_OLLAMA);
        cbProviderModus.valueProperty().addListener((obs, oud, nieuw) -> updateModusZichtbaarheid(nieuw));

        // CLIP instellingen
        txtClipModelPad.setText(config.getClipModelPad());
        sliderClipConfidence.setValue(config.getClipMinConfidence());
        lblClipConfidence.setText(config.getClipMinConfidence() + "%");
        sliderClipConfidence.valueProperty().addListener((obs, oud, nieuw) -> {
            lblClipConfidence.setText(nieuw.intValue() + "%");
        });
        controleerClipModel();

        // Ollama instellingen
        txtOllamaUrl.setText(config.getOllamaUrl());
        cbModel.setEditable(true);
        cbModel.setItems(FXCollections.observableArrayList("gemma3:4b", "llava:7b", "llava-phi3", "moondream"));
        cbModel.setValue(config.getModel());
        sliderConfidence.setValue(config.getMinConfidence());
        lblConfidence.setText(config.getMinConfidence() + "%");
        sliderConfidence.valueProperty().addListener((obs, oud, nieuw) -> {
            lblConfidence.setText(nieuw.intValue() + "%");
        });
        spTimeout.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 600, config.getTimeoutSeconds(), 10));

        // Camera instellingen
        cbCamera.setItems(FXCollections.observableArrayList(
                "Camera 0 (Standaard / Qualcomm)",
                "Camera 1 (Extern)",
                "Camera 2"
        ));
        int camIdx = Math.min(config.getCameraIndex(), cbCamera.getItems().size() - 1);
        cbCamera.getSelectionModel().select(Math.max(0, camIdx));
        chkAutoScan.setSelected(config.isAutoScan());

        updateModusZichtbaarheid(cbProviderModus.getValue());
    }

    private void updateModusZichtbaarheid(String modus) {
        boolean isClip = MODUS_CLIP.equals(modus);
        lblModusUitleg.setText(isClip ? "(Aanbevolen: Snel via CLIP &lt; 0.2s)" : "(Visueel LLM: 60-95s)");
        lblModusUitleg.setStyle(isClip ? "-fx-text-fill: #10B981; -fx-font-weight: bold;" : "-fx-text-fill: #F59E0B;");
    }

    @FXML
    private void kiesClipMap() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Kies de map met Xenova/clip-vit-base-patch32");
        File gekozen = chooser.showDialog(txtClipModelPad.getScene().getWindow());
        if (gekozen != null) {
            txtClipModelPad.setText(gekozen.getAbsolutePath());
            controleerClipModel();
        }
    }

    @FXML
    private void controleerClipModel() {
        String padTekst = txtClipModelPad.getText();
        config.setClipModelPad(padTekst);
        Path gevonden = config.vindBestaandClipModelPad();

        if (Files.exists(gevonden) && Files.exists(gevonden.resolve("tokenizer.json"))) {
            lblClipStatus.setText("✔ Modelbestanden gevonden in: " + gevonden.getFileName());
            lblClipStatus.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
        } else {
            lblClipStatus.setText("✖ tokenizer.json of onnx-bestanden ontbreken");
            lblClipStatus.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void testVerbinding() {
        lblVerbindingStatus.setText("Verbinding testen...");
        lblVerbindingStatus.setStyle("-fx-text-fill: #9CA3AF;");

        String ingevoerdeUrl = txtOllamaUrl.getText();
        config.setOllamaUrl(ingevoerdeUrl);
        ollamaProvider = new OllamaProvider(config);

        CompletableFuture.supplyAsync(() -> {
            try {
                return ollamaProvider.testVerbinding();
            } catch (Exception e) {
                return false;
            }
        }).thenAccept(succes -> Platform.runLater(() -> {
            if (succes) {
                lblVerbindingStatus.setText("✔ Verbonden met Ollama");
                lblVerbindingStatus.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
                verversModellen();
            } else {
                lblVerbindingStatus.setText("✖ Niet bereikbaar op " + ingevoerdeUrl);
                lblVerbindingStatus.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
            }
        }));
    }

    @FXML
    private void verversModellen() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return ollamaProvider.getBeschikbareModellen();
            } catch (Exception e) {
                return List.<String>of();
            }
        }).thenAccept(modellen -> Platform.runLater(() -> {
            if (!modellen.isEmpty()) {
                String huidigeKeuze = cbModel.getValue();
                cbModel.getItems().setAll(modellen);
                if (modellen.contains(huidigeKeuze)) {
                    cbModel.setValue(huidigeKeuze);
                } else if (!modellen.isEmpty()) {
                    cbModel.setValue(modellen.get(0));
                }
            }
        }));
    }

    @FXML
    private void opslaan() {
        // Modus
        boolean isClip = MODUS_CLIP.equals(cbProviderModus.getValue());
        config.setProviderModus(isClip ? "CLIP" : "OLLAMA");

        // CLIP instellingen
        config.setClipModelPad(txtClipModelPad.getText());
        config.setClipMinConfidence((int) Math.round(sliderClipConfidence.getValue()));

        // Ollama instellingen
        config.setOllamaUrl(txtOllamaUrl.getText());
        String geselecteerdModel = cbModel.getValue();
        if (geselecteerdModel != null && !geselecteerdModel.isBlank()) {
            config.setModel(geselecteerdModel.trim());
        }
        config.setMinConfidence((int) Math.round(sliderConfidence.getValue()));
        if (spTimeout.getValue() != null) {
            config.setTimeoutSeconds(spTimeout.getValue());
        }

        // Camera instellingen
        int camIndex = cbCamera.getSelectionModel().getSelectedIndex();
        config.setCameraIndex(Math.max(0, camIndex));
        config.setAutoScan(chkAutoScan.isSelected());

        config.opslaan();

        // Warm actieve provider direct op
        CompletableFuture.runAsync(AiProviderManager::warmUpActieveProvider);

        lblOpslaanStatus.setText("✔ Instellingen succesvol bewaard in ~/.sortsmart/config.properties");
        lblOpslaanStatus.setStyle("-fx-text-fill: #10B981;");
    }
}
