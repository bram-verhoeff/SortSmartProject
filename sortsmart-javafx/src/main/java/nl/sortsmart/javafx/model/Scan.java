package nl.sortsmart.javafx.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class Scan {
    private long scanId;
    private int stationId;
    private String stationSerienummer;
    private Integer itemtypeId;
    private String itemtypeNaam;
    private LocalDateTime tijdstip;
    private BigDecimal betrouwbaarheid;
    private String resultaat;

    public Scan(long scanId, int stationId, String stationSerienummer, Integer itemtypeId,
                String itemtypeNaam, LocalDateTime tijdstip, BigDecimal betrouwbaarheid, String resultaat) {
        this.scanId = scanId;
        this.stationId = stationId;
        this.stationSerienummer = stationSerienummer;
        this.itemtypeId = itemtypeId;
        this.itemtypeNaam = itemtypeNaam;
        this.tijdstip = tijdstip;
        this.betrouwbaarheid = betrouwbaarheid;
        this.resultaat = resultaat;
    }

    public long getScanId()                   { return scanId; }
    public int getStationId()                 { return stationId; }
    public String getStationSerienummer()     { return stationSerienummer; }
    public Integer getItemtypeId()            { return itemtypeId; }
    public String getItemtypeNaam()           { return itemtypeNaam; }
    public LocalDateTime getTijdstip()        { return tijdstip; }
    public BigDecimal getBetrouwbaarheid()    { return betrouwbaarheid; }
    public String getResultaat()              { return resultaat; }
}
