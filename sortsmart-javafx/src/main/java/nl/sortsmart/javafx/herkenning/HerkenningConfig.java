package nl.sortsmart.javafx.herkenning;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Beheert de configuratie-instellingen van de herkenning en camera.
 * Wordt opgeslagen in ~/.sortsmart/config.properties buiten het versiebeheer.
 */
public class HerkenningConfig {

    private static final String DEFAULT_OLLAMA_URL = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "gemma3:4b";
    private static final int DEFAULT_CAMERA_INDEX = 0;
    private static final boolean DEFAULT_AUTO_SCAN = true;
    private static final int DEFAULT_MIN_CONFIDENCE = 60;
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final String DEFAULT_PROVIDER_MODUS = "CLIP"; // "CLIP" (Snel) of "OLLAMA" (Nauwkeurig)
    private static final String DEFAULT_CLIP_MODEL_PAD = "../Sortsmart-main/.cache/models/Xenova/clip-vit-base-patch32";
    private static final int DEFAULT_CLIP_MIN_CONFIDENCE = 40;

    private String providerModus = DEFAULT_PROVIDER_MODUS;
    private String clipModelPad = DEFAULT_CLIP_MODEL_PAD;
    private int clipMinConfidence = DEFAULT_CLIP_MIN_CONFIDENCE;

    private String ollamaUrl = DEFAULT_OLLAMA_URL;
    private String model = DEFAULT_MODEL;
    private int cameraIndex = DEFAULT_CAMERA_INDEX;
    private boolean autoScan = DEFAULT_AUTO_SCAN;
    private int minConfidence = DEFAULT_MIN_CONFIDENCE;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    private static final HerkenningConfig INSTANTIE = new HerkenningConfig();

    private HerkenningConfig() {
        laad();
    }

    public static HerkenningConfig getInstantie() {
        return INSTANTIE;
    }

    private Path getConfigPad() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".sortsmart", "config.properties");
    }

    public synchronized void laad() {
        Path pad = getConfigPad();
        if (!Files.exists(pad)) {
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(pad)) {
            props.load(in);
            providerModus = props.getProperty("provider.modus", DEFAULT_PROVIDER_MODUS).trim();
            clipModelPad = props.getProperty("clip.model.pad", DEFAULT_CLIP_MODEL_PAD).trim();
            try {
                clipMinConfidence = Integer.parseInt(props.getProperty("clip.min.confidence", String.valueOf(DEFAULT_CLIP_MIN_CONFIDENCE)).trim());
            } catch (NumberFormatException ignored) {}
            ollamaUrl = props.getProperty("ollama.url", DEFAULT_OLLAMA_URL).trim();
            model = props.getProperty("ollama.model", DEFAULT_MODEL).trim();
            try {
                cameraIndex = Integer.parseInt(props.getProperty("camera.index", String.valueOf(DEFAULT_CAMERA_INDEX)).trim());
            } catch (NumberFormatException ignored) {}
            autoScan = Boolean.parseBoolean(props.getProperty("auto.scan", String.valueOf(DEFAULT_AUTO_SCAN)).trim());
            try {
                minConfidence = Integer.parseInt(props.getProperty("min.confidence", String.valueOf(DEFAULT_MIN_CONFIDENCE)).trim());
            } catch (NumberFormatException ignored) {}
            try {
                timeoutSeconds = Integer.parseInt(props.getProperty("timeout.seconds", String.valueOf(DEFAULT_TIMEOUT_SECONDS)).trim());
            } catch (NumberFormatException ignored) {}
        } catch (IOException e) {
            System.err.println("Fout bij laden van configuratie: " + e.getMessage());
        }
    }

    public synchronized void opslaan() {
        Path pad = getConfigPad();
        try {
            if (pad.getParent() != null && !Files.exists(pad.getParent())) {
                Files.createDirectories(pad.getParent());
            }
            Properties props = new Properties();
            props.setProperty("provider.modus", providerModus);
            props.setProperty("clip.model.pad", clipModelPad);
            props.setProperty("clip.min.confidence", String.valueOf(clipMinConfidence));
            props.setProperty("ollama.url", ollamaUrl);
            props.setProperty("ollama.model", model);
            props.setProperty("camera.index", String.valueOf(cameraIndex));
            props.setProperty("auto.scan", String.valueOf(autoScan));
            props.setProperty("min.confidence", String.valueOf(minConfidence));
            props.setProperty("timeout.seconds", String.valueOf(timeoutSeconds));

            try (OutputStream out = Files.newOutputStream(pad)) {
                props.store(out, "SortSmart Herkenning Instellingen");
            }
        } catch (IOException e) {
            System.err.println("Fout bij opslaan van configuratie: " + e.getMessage());
        }
    }

    public String getOllamaUrl() {
        return ollamaUrl;
    }

    public void setOllamaUrl(String ollamaUrl) {
        this.ollamaUrl = (ollamaUrl != null && !ollamaUrl.isBlank()) ? ollamaUrl.trim() : DEFAULT_OLLAMA_URL;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = (model != null && !model.isBlank()) ? model.trim() : DEFAULT_MODEL;
    }

    public int getCameraIndex() {
        return cameraIndex;
    }

    public void setCameraIndex(int cameraIndex) {
        this.cameraIndex = Math.max(0, cameraIndex);
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        this.autoScan = autoScan;
    }

    public int getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(int minConfidence) {
        this.minConfidence = Math.max(0, Math.min(100, minConfidence));
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(10, Math.min(600, timeoutSeconds));
    }

    public String getProviderModus() {
        return providerModus;
    }

    public void setProviderModus(String providerModus) {
        this.providerModus = ("OLLAMA".equalsIgnoreCase(providerModus)) ? "OLLAMA" : "CLIP";
    }

    public boolean isClipModus() {
        return "CLIP".equalsIgnoreCase(providerModus);
    }

    public String getClipModelPad() {
        return clipModelPad;
    }

    public void setClipModelPad(String clipModelPad) {
        this.clipModelPad = (clipModelPad != null && !clipModelPad.isBlank()) ? clipModelPad.trim() : DEFAULT_CLIP_MODEL_PAD;
    }

    public int getClipMinConfidence() {
        return clipMinConfidence;
    }

    public void setClipMinConfidence(int clipMinConfidence) {
        this.clipMinConfidence = Math.max(10, Math.min(100, clipMinConfidence));
    }

    /**
     * Zoekt het CLIP modelpad op schijf op:
     * 1. Geconfigureerd pad (relatief of absoluut)
     * 2. Relatief aan de repository root
     * 3. Fallback in ~/.sortsmart/models/clip-vit-base-patch32
     */
    public Path vindBestaandClipModelPad() {
        Path p = Paths.get(clipModelPad);
        if (Files.exists(p) && Files.exists(p.resolve("tokenizer.json"))) {
            return p.toAbsolutePath().normalize();
        }

        // Probeer relatief vanaf projectmap
        Path repoRel = Paths.get("..", "Sortsmart-main", ".cache", "models", "Xenova", "clip-vit-base-patch32");
        if (Files.exists(repoRel) && Files.exists(repoRel.resolve("tokenizer.json"))) {
            return repoRel.toAbsolutePath().normalize();
        }

        Path repoLocal = Paths.get("Sortsmart-main", ".cache", "models", "Xenova", "clip-vit-base-patch32");
        if (Files.exists(repoLocal) && Files.exists(repoLocal.resolve("tokenizer.json"))) {
            return repoLocal.toAbsolutePath().normalize();
        }

        // Fallback in gebruikersmap
        Path userHomeFallback = Paths.get(System.getProperty("user.home"), ".sortsmart", "models", "clip-vit-base-patch32");
        return userHomeFallback.toAbsolutePath().normalize();
    }
}
