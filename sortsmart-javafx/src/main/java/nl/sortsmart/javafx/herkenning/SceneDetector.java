package nl.sortsmart.javafx.herkenning;

/**
 * Detecteert wanneer er een nieuw afvalitem voor de camera wordt geplaatst en tot stilstand komt.
 * Gebaseerd op de beproefde algoritmes uit live.ts:
 * - Vergelijkt opeenvolgende kleine grijswaardenframes (bijv. 48x36).
 * - Leert de lege achtergrond (baseline).
 * - Wacht tot het item minimaal een instelbare tijd (bijv. 350 ms) stil ligt.
 * - Triggert precies 1 scan per itembezoek.
 */
public class SceneDetector {

    public enum Fase {
        KALIBREREN,
        LEEG,
        BEWEGEND,
        STABILISEREN,
        KLAAR_VOOR_SCAN,
        VASTGEHOUDEN
    }

    public static class Update {
        private final Fase fase;
        private final boolean scan;

        public Update(Fase fase, boolean scan) {
            this.fase = fase;
            this.scan = scan;
        }

        public Fase getFase() {
            return fase;
        }

        public boolean isScan() {
            return scan;
        }
    }

    private final double bewegingsDrempel;   // Fractie pixels die moet veranderen tussen frames (bijv. 0.06)
    private final double veranderingsDrempel; // Fractie pixels die moet afwijken van lege achtergrond (bijv. 0.05)
    private final long stabielVereistMs;      // Hoe lang beeld stil moet liggen (bijv. 350 ms)
    private final long maxWachtTijdMs;        // Hand trilt altijd iets: na bijv. 1500 ms toch scannen als het bijna stil ligt
    private final int pixelTolerantie;        // Sensorruis per pixel (0-255)

    private static final double BIJNA_STIL = 0.20;

    private byte[] vorigFrame = null;
    private byte[] achtergrond = null;
    private byte[] gescandFrame = null;

    private long stabielSinds = 0;
    private long itemAanwezigSinds = 0;
    private boolean gewachtEnGescand = false;

    public SceneDetector() {
        this(0.06, 0.05, 350, 1500, 14);
    }

    public SceneDetector(double bewegingsDrempel, double veranderingsDrempel,
                         long stabielVereistMs, long maxWachtTijdMs, int pixelTolerantie) {
        this.bewegingsDrempel = bewegingsDrempel;
        this.veranderingsDrempel = veranderingsDrempel;
        this.stabielVereistMs = stabielVereistMs;
        this.maxWachtTijdMs = maxWachtTijdMs;
        this.pixelTolerantie = pixelTolerantie;
    }

    /**
     * Reset de detector zodat het eerstvolgende stabiele frame als nieuwe lege achtergrond dient.
     */
    public synchronized void reset() {
        vorigFrame = null;
        achtergrond = null;
        gescandFrame = null;
        stabielSinds = 0;
        itemAanwezigSinds = 0;
        gewachtEnGescand = false;
    }

    /**
     * Staat opnieuw een scan toe als een vorige poging mislukte.
     */
    public synchronized void vergeetLaatsteScan() {
        gescandFrame = null;
        gewachtEnGescand = false;
    }

    /**
     * Verwerkt een klein grijswaardenframe en bepaalt of een scan getriggerd moet worden.
     *
     * @param frame Grijswaardenpixels (bijv. 48 * 36 bytes)
     * @param nuMs Huidig tijdstip in milliseconden
     * @return Update met huidige fase en scan-vlag
     */
    public synchronized Update verwerkFrame(byte[] frame, long nuMs) {
        if (frame == null || frame.length == 0) {
            return new Update(Fase.LEEG, false);
        }

        byte[] vorig = this.vorigFrame;
        this.vorigFrame = frame.clone();

        if (vorig == null) {
            this.stabielSinds = nuMs;
            return new Update(achtergrond != null ? Fase.BEWEGEND : Fase.KALIBREREN, false);
        }

        // Is het beeld gelijk aan de geleerde lege achtergrond?
        boolean isLeeg = (achtergrond != null) &&
                (berekenVerschil(frame, achtergrond, pixelTolerantie) < veranderingsDrempel);

        if (isLeeg || achtergrond == null) {
            itemAanwezigSinds = 0;
            gewachtEnGescand = false;
        } else if (itemAanwezigSinds == 0) {
            itemAanwezigSinds = nuMs;
        }

        double beweging = berekenVerschil(frame, vorig, pixelTolerantie);

        if (beweging > bewegingsDrempel) {
            // Beeld beweegt nog te veel
            stabielSinds = nuMs;

            // Als het item er al lang ligt en bijna stil is (bijv. trillende hand)
            if (itemAanwezigSinds > 0 && !gewachtEnGescand &&
                    (nuMs - itemAanwezigSinds >= maxWachtTijdMs) && (beweging < BIJNA_STIL)) {
                gewachtEnGescand = true;
                gescandFrame = frame.clone();
                return new Update(Fase.KLAAR_VOOR_SCAN, true);
            }

            if (achtergrond == null) {
                return new Update(Fase.KALIBREREN, false);
            }
            return new Update(isLeeg ? Fase.LEEG : Fase.BEWEGEND, false);
        }

        // Beweging is onder de drempelwaarde: beeld houdt stil
        if (achtergrond == null) {
            // Eerste keer stabiel: sla op als lege achtergrond
            if (nuMs - stabielSinds >= stabielVereistMs) {
                achtergrond = frame.clone();
                return new Update(Fase.LEEG, false);
            }
            return new Update(Fase.KALIBREREN, false);
        }

        if (isLeeg) {
            // Geen item in beeld, rusttoestand
            return new Update(Fase.LEEG, false);
        }

        // Er ligt een item in beeld en het beweegt nauwelijks
        long stilStaatDuur = nuMs - stabielSinds;
        if (stilStaatDuur < stabielVereistMs) {
            return new Update(Fase.STABILISEREN, false);
        }

        // Het item ligt lang genoeg stil: controleer of we dit specifieke item al gescand hebben
        if (gescandFrame != null && berekenVerschil(frame, gescandFrame, pixelTolerantie) < veranderingsDrempel) {
            // Al gescand, wacht tot item weggenomen wordt
            return new Update(Fase.VASTGEHOUDEN, false);
        }

        // Precies 1 scan triggeren voor dit item
        gescandFrame = frame.clone();
        gewachtEnGescand = true;
        return new Update(Fase.KLAAR_VOOR_SCAN, true);
    }

    public static double berekenVerschil(byte[] a, byte[] b, int tolerantie) {
        if (a.length != b.length || a.length == 0) {
            return 1.0;
        }
        int gewijzigd = 0;
        for (int i = 0; i < a.length; i++) {
            int valA = a[i] & 0xFF;
            int valB = b[i] & 0xFF;
            if (Math.abs(valA - valB) > tolerantie) {
                gewijzigd++;
            }
        }
        return (double) gewijzigd / a.length;
    }
}
