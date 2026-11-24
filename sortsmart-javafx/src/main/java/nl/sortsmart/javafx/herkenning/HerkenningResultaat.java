package nl.sortsmart.javafx.herkenning;

/**
 * Resultaat van een afvalclassificatie door een herkenningsprovider.
 */
public class HerkenningResultaat {

    private final String fractie;
    private final String itemtype;
    private final int betrouwbaarheid;
    private final boolean meerdereItems;
    private final boolean vloeistof;
    private final String uitleg;
    private final String resultaatType;
    private final boolean geldig;
    private long latencyMs = 0;

    public HerkenningResultaat(String fractie, String itemtype, int betrouwbaarheid,
                               boolean meerdereItems, boolean vloeistof, String uitleg,
                               String resultaatType, boolean geldig) {
        this.fractie = fractie;
        this.itemtype = itemtype;
        this.betrouwbaarheid = betrouwbaarheid;
        this.meerdereItems = meerdereItems;
        this.vloeistof = vloeistof;
        this.uitleg = uitleg;
        this.resultaatType = resultaatType;
        this.geldig = geldig;
    }

    public static HerkenningResultaat nietHerkend(String uitleg) {
        return new HerkenningResultaat(null, null, 0, false, false, uitleg, "NietHerkend", false);
    }

    public static HerkenningResultaat vloeistof(String uitleg) {
        return new HerkenningResultaat(null, null, 0, false, true, uitleg, "VloeistofGedetecteerd", true);
    }

    public static HerkenningResultaat meerdereItems(String uitleg) {
        return new HerkenningResultaat(null, null, 0, true, false, uitleg, "MeerdereItems", true);
    }

    public static HerkenningResultaat gesorteerd(String fractie, String itemtype, int betrouwbaarheid, String uitleg) {
        return new HerkenningResultaat(fractie, itemtype, betrouwbaarheid, false, false, uitleg, "Gesorteerd", true);
    }

    public String getFractie() {
        return fractie;
    }

    public String getItemtype() {
        return itemtype;
    }

    public int getBetrouwbaarheid() {
        return betrouwbaarheid;
    }

    public boolean isMeerdereItems() {
        return meerdereItems;
    }

    public boolean isVloeistof() {
        return vloeistof;
    }

    public String getUitleg() {
        return uitleg;
    }

    public String getResultaatType() {
        return resultaatType;
    }

    public boolean isGeldig() {
        return geldig;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public HerkenningResultaat metLatency(long latencyMs) {
        this.latencyMs = latencyMs;
        return this;
    }

    @Override
    public String toString() {
        return "HerkenningResultaat{" +
                "fractie='" + fractie + '\'' +
                ", itemtype='" + itemtype + '\'' +
                ", betrouwbaarheid=" + betrouwbaarheid +
                ", resultaatType='" + resultaatType + '\'' +
                '}';
    }
}
