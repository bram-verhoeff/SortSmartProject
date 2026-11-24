package nl.sortsmart.javafx.herkenning;

/**
 * Beheert en levert de actieve AI-herkenningsprovider (CLIP of Ollama)
 * op basis van de huidige configuratie.
 */
public class AiProviderManager {

    private static final HerkenningConfig config = HerkenningConfig.getInstantie();
    private static final ClipProvider clipProvider = new ClipProvider(config);
    private static final OllamaProvider ollamaProvider = new OllamaProvider(config);

    public static AiProvider getActieveProvider() {
        if (config.isClipModus()) {
            return clipProvider;
        } else {
            return ollamaProvider;
        }
    }

    public static ClipProvider getClipProvider() {
        return clipProvider;
    }

    public static OllamaProvider getOllamaProvider() {
        return ollamaProvider;
    }

    public static int getActieveMinConfidence() {
        if (config.isClipModus()) {
            return config.getClipMinConfidence();
        } else {
            return config.getMinConfidence();
        }
    }

    public static void warmUpActieveProvider() {
        try {
            getActieveProvider().warmUp();
        } catch (Exception e) {
            System.err.println("[AiProviderManager] Warm-up mislukt: " + e.getMessage());
        }
    }
}
