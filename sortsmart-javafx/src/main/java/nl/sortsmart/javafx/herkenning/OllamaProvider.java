package nl.sortsmart.javafx.herkenning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-herkenningsprovider die communiceert met een lokale Ollama-instantie (bijv. gemma3:4b).
 * Bevat beeldverkleining, hash-caching en herpogingen bij tijdelijke verbindingsproblemen.
 */
public class OllamaProvider implements AiProvider {

    private final HerkenningConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, HerkenningResultaat> cache = new ConcurrentHashMap<>();

    private static final int MAX_CACHE_GROOTTE = 150;
    private static final int MAX_HERHALINGEN = 2;

    public OllamaProvider() {
        this(HerkenningConfig.getInstantie());
    }

    public OllamaProvider(HerkenningConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getNaam() {
        return "Ollama (" + config.getModel() + ")";
    }

    @Override
    public HerkenningResultaat herkenAfval(byte[] jpegAfbeelding, int minBetrouwbaarheid) throws Exception {
        if (jpegAfbeelding == null || jpegAfbeelding.length == 0) {
            return HerkenningResultaat.nietHerkend("Geen afbeelding ontvangen");
        }

        // 1. Verklein beeld naar max. 512 pixels aan langste zijde met 0.8 JPEG-kwaliteit
        byte[] verkleindBeeld = verkleinBeeld(jpegAfbeelding, 512, 0.8f);

        String hash = berekenHash(verkleindBeeld);
        if (cache.containsKey(hash)) {
            return cache.get(hash);
        }

        String base64Foto = Base64.getEncoder().encodeToString(verkleindBeeld);

        String systemPrompt = """
                Je bent een afvalsensor voor een slimme SortSmart-afvalbak.
                Je classificeert een enkel afvalitem op een foto in precies een van de volgende 4 fracties:
                - Papier (papier, kranten, karton, droge broodzak)
                - PMD (plastic flessen, frisdrankblikjes, aluminium bakjes, drinkpakken)
                - Rest (of Restafval: koffiebekers, tissues, gemengd afval, vervuild papier)
                - Etensresten (groente-, fruitresten, klokhuizen, etensresten)
                
                Geef ALTIJD en UITSLUITEND een valide JSON-object terug volgens dit schema:
                {
                  "fractie": "Papier" | "PMD" | "Rest" | "Etensresten" | "Onbekend",
                  "itemtype": "korte naam van het voorwerp (bijv. Frisdrankblikje, Koffiebeker, Appelklokhuis)",
                  "betrouwbaarheid": getal tussen 0 en 100,
                  "meerdereItems": true als er meerdere voorwerpen tegelijk zichtbaar zijn anders false,
                  "vloeistof": true als er vloeistof of lekkage zichtbaar is anders false,
                  "uitleg": "korte toelichting in 1 zin"
                }
                Als het beeld onduidelijk is of geen afval bevat, zet fractie op "Onbekend" en betrouwbaarheid laag.
                """;

        String userPrompt = "Classificeer het item op deze foto strikt als JSON.";

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("model", config.getModel());
        requestPayload.put("stream", false);
        requestPayload.put("format", "json");
        requestPayload.put("keep_alive", "30m");

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        userMessage.put("images", List.of(base64Foto));

        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        requestPayload.put("messages", List.of(systemMessage, userMessage));

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.0);
        requestPayload.put("options", options);

        String jsonBody = objectMapper.writeValueAsString(requestPayload);
        String endpoint = formatteerUrl(config.getOllamaUrl(), "/api/chat");

        String antwoordJson = voerAanroepUitMetHerhaling(endpoint, jsonBody);

        HerkenningResultaat resultaat = parseEnValideerAntwoord(antwoordJson, minBetrouwbaarheid);

        // Resultaat in cache opslaan
        if (cache.size() >= MAX_CACHE_GROOTTE) {
            cache.clear();
        }
        cache.put(hash, resultaat);

        return resultaat;
    }

    private String voerAanroepUitMetHerhaling(String endpoint, String jsonBody) throws Exception {
        Exception laatsteFout = null;
        int timeoutSec = config.getTimeoutSeconds();

        for (int poging = 0; poging <= MAX_HERHALINGEN; poging++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(timeoutSec))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    return response.body();
                }

                // Alleen bij serverfouten (HTTP 5xx) opnieuw proberen
                if (statusCode >= 500 && statusCode <= 504 && poging < MAX_HERHALINGEN) {
                    Thread.sleep(600L * (poging + 1));
                    continue;
                }

                throw new IOException("Ollama HTTP-fout (" + statusCode + "): " + response.body());
            } catch (HttpTimeoutException te) {
                // Time-out: direct stoppen en NIET herhalen om minuten wachten te voorkomen
                throw new IOException("Ollama time-out na " + timeoutSec + " seconden.", te);
            } catch (IOException e) {
                laatsteFout = e;
                if (poging < MAX_HERHALINGEN) {
                    try {
                        Thread.sleep(600L * (poging + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }

        throw laatsteFout != null ? laatsteFout : new IOException("Ollama-aanroep mislukt na herpogingen.");
    }

    private HerkenningResultaat parseEnValideerAntwoord(String responseBody, int minBetrouwbaarheid) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("message");
            String content = message.path("content").asText();

            if (content == null || content.isBlank()) {
                return HerkenningResultaat.nietHerkend("Leeg antwoord van Ollama");
            }

            // Parseer de geretourneerde JSON van het model
            JsonNode modelOutput = objectMapper.readTree(content);

            boolean meerdereItems = modelOutput.path("meerdereItems").asBoolean(false);
            if (meerdereItems) {
                return HerkenningResultaat.meerdereItems(modelOutput.path("uitleg").asText("Meerdere items gedetecteerd"));
            }

            boolean vloeistof = modelOutput.path("vloeistof").asBoolean(false);
            if (vloeistof) {
                return HerkenningResultaat.vloeistof(modelOutput.path("uitleg").asText("Vloeistof of lekkage gedetecteerd"));
            }

            int betrouwbaarheid = modelOutput.path("betrouwbaarheid").asInt(0);
            String fractieRaw = modelOutput.path("fractie").asText("").trim();
            String itemtype = modelOutput.path("itemtype").asText("Onbekend voorwerp").trim();
            String uitleg = modelOutput.path("uitleg").asText("").trim();

            String genormaliseerdeFractie = normaliseerFractie(fractieRaw);

            if (genormaliseerdeFractie == null || betrouwbaarheid < minBetrouwbaarheid) {
                return HerkenningResultaat.nietHerkend(
                        "Lage betrouwbaarheid (" + betrouwbaarheid + "% < " + minBetrouwbaarheid + "%) of onbekende fractie: " + fractieRaw
                );
            }

            return HerkenningResultaat.gesorteerd(genormaliseerdeFractie, itemtype, betrouwbaarheid, uitleg);

        } catch (Exception e) {
            return HerkenningResultaat.nietHerkend("Ongeldige JSON ontvangen van model: " + e.getMessage());
        }
    }

    private String normaliseerFractie(String invoer) {
        if (invoer == null) return null;
        String schoon = invoer.trim().toLowerCase();

        // 1. Eerst controleren op exacte namen
        if (schoon.equals("etensresten") || schoon.equals("eten") || schoon.equals("gft")) return "Etensresten";
        if (schoon.equals("papier") || schoon.equals("karton")) return "Papier";
        if (schoon.equals("pmd") || schoon.equals("plastic")) return "PMD";
        if (schoon.equals("rest") || schoon.equals("restafval")) return "Rest";

        // 2. Pas daarna op deelwoorden (eten/gft/organ/bio ALTIJD vóór rest!)
        if (schoon.contains("eten") || schoon.contains("bio") || schoon.contains("gft") || schoon.contains("organ")) {
            return "Etensresten";
        }
        if (schoon.contains("papier") || schoon.contains("karton")) {
            return "Papier";
        }
        if (schoon.contains("pmd") || schoon.contains("plastic") || schoon.contains("blik")) {
            return "PMD";
        }
        if (schoon.contains("rest")) {
            return "Rest";
        }

        return null;
    }

    /**
     * Verkleint een afbeelding in het geheugen naar maximaal maxLengte aan de langste zijde
     * en comprimeert naar JPEG met de opgegeven kwaliteit (0.0 - 1.0).
     */
    private byte[] verkleinBeeld(byte[] origineel, int maxLengte, float kwaliteit) {
        if (origineel == null || origineel.length == 0) return origineel;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(origineel);
            BufferedImage src = ImageIO.read(bais);
            if (src == null) return origineel;

            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= maxLengte && h <= maxLengte) {
                return origineel;
            }

            double schaal = (double) maxLengte / Math.max(w, h);
            int nw = Math.max(1, (int) Math.round(w * schaal));
            int nh = Math.max(1, (int) Math.round(h * schaal));

            BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) return origineel;

            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(kwaliteit);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(resized, null, null), param);
            }
            writer.dispose();

            return baos.toByteArray();
        } catch (Exception e) {
            return origineel;
        }
    }

    @Override
    public void warmUp() {
        try {
            String endpoint = formatteerUrl(config.getOllamaUrl(), "/api/chat");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", config.getModel());
            payload.put("messages", List.of());
            payload.put("keep_alive", "30m");
            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            System.out.println("[OllamaProvider] Model " + config.getModel() + " opgewarmd met keep_alive 30m.");
        } catch (Exception e) {
            System.err.println("[OllamaProvider] Opwarmen van model overgeslagen: " + e.getMessage());
        }
    }

    @Override
    public boolean testVerbinding() throws Exception {
        String endpoint = formatteerUrl(config.getOllamaUrl(), "/api/tags");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    @Override
    public List<String> getBeschikbareModellen() throws Exception {
        String endpoint = formatteerUrl(config.getOllamaUrl(), "/api/tags");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Fout bij ophalen van modellen (" + response.statusCode() + ")");
        }

        List<String> modellen = new ArrayList<>();
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode modelsNode = root.path("models");
        if (modelsNode.isArray()) {
            for (JsonNode m : modelsNode) {
                String naam = m.path("name").asText();
                if (!naam.isBlank()) {
                    modellen.add(naam);
                }
            }
        }
        return modellen;
    }

    private String berekenHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(Arrays.hashCode(data));
        }
    }

    private String formatteerUrl(String basis, String pad) {
        String base = basis.endsWith("/") ? basis.substring(0, basis.length() - 1) : basis;
        String path = pad.startsWith("/") ? pad : "/" + pad;
        return base + path;
    }
}
