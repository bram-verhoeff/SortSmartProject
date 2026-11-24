package nl.sortsmart.javafx.herkenning;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Snelle lokale afvalclassificatie via CLIP (Xenova/clip-vit-base-patch32).
 * Verwerkt beelden en prompts met ONNX Runtime in milliseconden.
 */
public class ClipProvider implements AiProvider {

    private final HerkenningConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrtEnvironment env;
    private OrtSession textSession;
    private OrtSession visionSession;
    private HuggingFaceTokenizer tokenizer;

    private boolean geladen = false;
    private String laadFout = null;

    // Gemiddelde L2-genormaliseerde embeddings per klasse (Papier, PMD, Etensresten, Rest, none)
    private final Map<String, float[]> classEmbeddings = new LinkedHashMap<>();
    private final List<String> klassenVolgorde = new ArrayList<>();

    // Individuele prompts voor itemtype-bepaling
    private static class PromptItem {
        final String klasse;
        final String promptTekst;
        final String itemNaam;
        final float[] embedding;

        PromptItem(String klasse, String promptTekst, String itemNaam, float[] embedding) {
            this.klasse = klasse;
            this.promptTekst = promptTekst;
            this.itemNaam = itemNaam;
            this.embedding = embedding;
        }
    }
    private final List<PromptItem> allePrompts = new ArrayList<>();

    private static final float[] CLIP_MEAN = {0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] CLIP_STD  = {0.26862954f, 0.26130258f, 0.27577711f};
    private static final int MAX_SEQ_LEN = 77;
    private static final long PAD_TOKEN_ID = 49407L;

    public ClipProvider() {
        this(HerkenningConfig.getInstantie());
    }

    public ClipProvider(HerkenningConfig config) {
        this.config = config;
    }

    @Override
    public String getNaam() {
        return "CLIP (Snel, lokaal)";
    }

    public synchronized void initialiseer() throws Exception {
        if (geladen) return;

        Path modelMap = config.vindBestaandClipModelPad();
        if (!Files.exists(modelMap)) {
            laadFout = "CLIP modelmap niet gevonden: " + modelMap;
            throw new IllegalStateException(laadFout);
        }

        Path tokenizerPad = modelMap.resolve("tokenizer.json");
        Path textModelPad = modelMap.resolve("onnx").resolve("text_model_quantized.onnx");
        if (!Files.exists(textModelPad)) {
            textModelPad = modelMap.resolve("onnx").resolve("text_model.onnx");
        }

        Path visionModelPad = modelMap.resolve("onnx").resolve("vision_model_quantized.onnx");
        if (!Files.exists(visionModelPad)) {
            visionModelPad = modelMap.resolve("onnx").resolve("vision_model.onnx");
        }

        if (!Files.exists(tokenizerPad) || !Files.exists(textModelPad) || !Files.exists(visionModelPad)) {
            laadFout = "Niet alle benodigde CLIP ONNX-bestanden zijn aanwezig in " + modelMap;
            throw new IllegalStateException(laadFout);
        }

        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(Math.min(4, Runtime.getRuntime().availableProcessors()));

        textSession = env.createSession(textModelPad.toString(), opts);
        visionSession = env.createSession(visionModelPad.toString(), opts);
        tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPad);

        berekenTekstEmbeddings();
        geladen = true;
        laadFout = null;
        System.out.println("[ClipProvider] CLIP succesvol geladen vanuit " + modelMap);
    }

    private void berekenTekstEmbeddings() throws Exception {
        Map<String, List<String>> promptKaart = laadPrompts();

        List<String> promptLijst = new ArrayList<>();
        List<String> promptKlasseLijst = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : promptKaart.entrySet()) {
            String klasse = entry.getKey();
            for (String p : entry.getValue()) {
                promptLijst.add(p);
                promptKlasseLijst.add(klasse);
            }
        }

        int n = promptLijst.size();
        if (n == 0) return;

        long[] inputIds = new long[n * MAX_SEQ_LEN];
        Arrays.fill(inputIds, PAD_TOKEN_ID);

        for (int i = 0; i < n; i++) {
            Encoding enc = tokenizer.encode(promptLijst.get(i));
            long[] ids = enc.getIds();
            int len = Math.min(ids.length, MAX_SEQ_LEN);
            System.arraycopy(ids, 0, inputIds, i * MAX_SEQ_LEN, len);
        }

        OnnxTensor textTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[]{n, MAX_SEQ_LEN});
        OrtSession.Result result = textSession.run(Collections.singletonMap("input_ids", textTensor));

        float[][] textEmbeddings = (float[][]) result.get(0).getValue();
        int embDim = textEmbeddings[0].length;

        // Normaliseer elke prompt embedding en sla op
        allePrompts.clear();
        Map<String, List<float[]>> klassePromptEmbeddings = new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            float[] emb = textEmbeddings[i];
            l2Normaliseer(emb);

            String klasse = promptKlasseLijst.get(i);
            String promptTekst = promptLijst.get(i);
            String itemNaam = parseItemtype(promptTekst);

            allePrompts.add(new PromptItem(klasse, promptTekst, itemNaam, emb));
            klassePromptEmbeddings.computeIfAbsent(klasse, k -> new ArrayList<>()).add(emb);
        }

        // Bereken gemiddelde genormaliseerde vector per klasse
        classEmbeddings.clear();
        klassenVolgorde.clear();

        for (Map.Entry<String, List<float[]>> entry : klassePromptEmbeddings.entrySet()) {
            String klasse = entry.getKey();
            List<float[]> embs = entry.getValue();

            float[] gem = new float[embDim];
            for (float[] v : embs) {
                for (int d = 0; d < embDim; d++) {
                    gem[d] += v[d];
                }
            }
            l2Normaliseer(gem);

            classEmbeddings.put(klasse, gem);
            klassenVolgorde.add(klasse);
        }
    }

    private Map<String, List<String>> laadPrompts() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            InputStream is = getClass().getResourceAsStream("/clip-prompts.json");
            if (is == null) {
                is = getClass().getResourceAsStream("/nl/sortsmart/javafx/herkenning/clip-prompts.json");
            }
            if (is != null) {
                JsonNode root = objectMapper.readTree(is);
                JsonNode classesNode = root.path("classes");
                var it = classesNode.fields();
                while (it.hasNext()) {
                    var entry = it.next();
                    List<String> list = new ArrayList<>();
                    if (entry.getValue().isArray()) {
                        for (JsonNode elem : entry.getValue()) {
                            list.add(elem.asText());
                        }
                    }
                    result.put(entry.getKey(), list);
                }
            }
        } catch (Exception e) {
            System.err.println("[ClipProvider] Fout bij laden clip-prompts.json: " + e.getMessage());
        }

        // Fallback als bestand niet geladen kon worden
        if (result.isEmpty()) {
            result.put("Papier", List.of("a photo of paper", "a photo of a cardboard box", "a photo of a newspaper", "a photo of a paper bag", "a photo of an egg carton"));
            result.put("PMD", List.of("a photo of plastic packaging", "a photo of a plastic food container", "a photo of a tin can", "a photo of a plastic bag", "a photo of a yogurt cup", "a photo of a plastic drink bottle", "a photo of a plastic water bottle", "a photo of a soda can", "a photo of a drink carton"));
            result.put("Etensresten", List.of("a photo of fruit", "a photo of vegetables", "a photo of food waste", "a photo of a banana peel", "a photo of an apple core"));
            result.put("Rest", List.of("a photo of a disposable coffee cup", "a photo of a paper coffee cup with a plastic lid", "a photo of a takeaway coffee cup", "a photo of a dirty pizza box", "a photo of a plastic straw", "a crisp packet", "a photo of a used tissue", "a photo of a candy wrapper", "a photo of a glass bottle"));
            result.put("none", List.of("a photo of a person's face", "a photo of an empty room", "a photo of a wall"));
        }
        return result;
    }

    @Override
    public HerkenningResultaat herkenAfval(byte[] jpegAfbeelding, int minBetrouwbaarheid) throws Exception {
        if (!geladen) {
            initialiseer();
        }

        long startTijd = System.currentTimeMillis();

        if (jpegAfbeelding == null || jpegAfbeelding.length == 0) {
            return HerkenningResultaat.nietHerkend("Geen afbeelding ontvangen").metLatency(0);
        }

        BufferedImage src = ImageIO.read(new ByteArrayInputStream(jpegAfbeelding));
        if (src == null) {
            return HerkenningResultaat.nietHerkend("Ongeldig afbeeldingsformaat").metLatency(0);
        }

        // 1. Schaal kortste zijde naar 224 (bicubic)
        int w = src.getWidth();
        int h = src.getHeight();
        int nw, nh;
        if (w < h) {
            nw = 224;
            nh = Math.max(224, (int) Math.round((double) h * 224.0 / w));
        } else {
            nh = 224;
            nw = Math.max(224, (int) Math.round((double) w * 224.0 / h));
        }

        BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(src, 0, 0, nw, nh, null);
        g2d.dispose();

        // 2. Midden 224x224 bijsnijden
        int cropX = (nw - 224) / 2;
        int cropY = (nh - 224) / 2;
        BufferedImage cropped = resized.getSubimage(cropX, cropY, 224, 224);

        // 3. Normaliseren en CHW vullen
        float[] chw = new float[1 * 3 * 224 * 224];
        int spatial = 224 * 224;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 224; x++) {
                int rgb = cropped.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;

                chw[0 * spatial + y * 224 + x] = (r - CLIP_MEAN[0]) / CLIP_STD[0];
                chw[1 * spatial + y * 224 + x] = (g - CLIP_MEAN[1]) / CLIP_STD[1];
                chw[2 * spatial + y * 224 + x] = (b - CLIP_MEAN[2]) / CLIP_STD[2];
            }
        }

        // 4. Inferenz vision model
        OnnxTensor visionTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, 224, 224});
        OrtSession.Result result = visionSession.run(Collections.singletonMap("pixel_values", visionTensor));
        float[][] imageEmbeddings = (float[][]) result.get(0).getValue();
        float[] imageEmb = imageEmbeddings[0];
        l2Normaliseer(imageEmb);

        // 5. Softmax over 100 * dot(beeld, klasse)
        int k = klassenVolgorde.size();
        double[] logits = new double[k];
        for (int i = 0; i < k; i++) {
            String klasse = klassenVolgorde.get(i);
            float[] classEmb = classEmbeddings.get(klasse);
            double inwendigProduct = inwendigProduct(imageEmb, classEmb);
            logits[i] = 100.0 * inwendigProduct;
        }

        double[] kansen = softmax(logits);

        int besteIndex = 0;
        double hoogsteKans = kansen[0];
        for (int i = 1; i < k; i++) {
            if (kansen[i] > hoogsteKans) {
                hoogsteKans = kansen[i];
                besteIndex = i;
            }
        }

        String winnendeKlasse = klassenVolgorde.get(besteIndex);
        int betrouwbaarheid = (int) Math.round(hoogsteKans * 100.0);
        long latencyMs = System.currentTimeMillis() - startTijd;

        // Als 'none' wint of de score onder de drempelwaarde ligt
        if ("none".equalsIgnoreCase(winnendeKlasse) || betrouwbaarheid < minBetrouwbaarheid) {
            return HerkenningResultaat.nietHerkend(
                    "Lage zekerheid (" + betrouwbaarheid + "% < " + minBetrouwbaarheid + "%) of geen afval (" + winnendeKlasse + ")"
            ).metLatency(latencyMs);
        }

        // 6. Zoek het best passende itemtype binnen de winnende klasse
        String bestItemtype = "Afvalitem";
        double bestePromptSim = -1.0;

        for (PromptItem pi : allePrompts) {
            if (pi.klasse.equalsIgnoreCase(winnendeKlasse)) {
                double sim = inwendigProduct(imageEmb, pi.embedding);
                if (sim > bestePromptSim) {
                    bestePromptSim = sim;
                    bestItemtype = pi.itemNaam;
                }
            }
        }

        return HerkenningResultaat.gesorteerd(
                winnendeKlasse,
                bestItemtype,
                betrouwbaarheid,
                "Herkend als " + winnendeKlasse + " via CLIP (" + betrouwbaarheid + "%)"
        ).metLatency(latencyMs);
    }

    private String parseItemtype(String prompt) {
        String s = prompt.trim();
        String lower = s.toLowerCase();
        if (lower.startsWith("a photo of an ")) {
            s = s.substring(14).trim();
        } else if (lower.startsWith("a photo of a ")) {
            s = s.substring(13).trim();
        } else if (lower.startsWith("a photo of ")) {
            s = s.substring(11).trim();
        }
        if (s.isEmpty()) return "Afvalitem";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void l2Normaliseer(float[] v) {
        double som = 0;
        for (float f : v) {
            som += (double) f * f;
        }
        double norm = Math.sqrt(som);
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
    }

    private double inwendigProduct(float[] a, float[] b) {
        double som = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            som += (double) a[i] * b[i];
        }
        return som;
    }

    private double[] softmax(double[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (double l : logits) {
            if (l > max) max = l;
        }
        double som = 0;
        double[] exp = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = Math.exp(logits[i] - max);
            som += exp[i];
        }
        for (int i = 0; i < logits.length; i++) {
            exp[i] = som > 0 ? (exp[i] / som) : 0;
        }
        return exp;
    }

    @Override
    public void warmUp() {
        try {
            initialiseer();
        } catch (Exception e) {
            System.err.println("[ClipProvider] Warm-up mislukt: " + e.getMessage());
        }
    }

    @Override
    public boolean testVerbinding() throws Exception {
        try {
            initialiseer();
            return geladen;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> getBeschikbareModellen() throws Exception {
        return List.of("Xenova/clip-vit-base-patch32 (lokaal)");
    }
}
