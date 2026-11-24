package nl.sortsmart.javafx.model;

import java.time.LocalDateTime;

public class Melding {
    private int meldingId;
    private int stationId;
    private String stationSerienummer;
    private Integer vaknummer;
    private Integer afgehandeldDoor;
    private String type;
    private String omschrijving;
    private LocalDateTime tijdstipGemeld;
    private String status;
    private LocalDateTime afgehandeldOp;

    public Melding(int meldingId, int stationId, String stationSerienummer, Integer vaknummer,
                   Integer afgehandeldDoor, String type, String omschrijving,
                   LocalDateTime tijdstipGemeld, String status, LocalDateTime afgehandeldOp) {
        this.meldingId = meldingId;
        this.stationId = stationId;
        this.stationSerienummer = stationSerienummer;
        this.vaknummer = vaknummer;
        this.afgehandeldDoor = afgehandeldDoor;
        this.type = type;
        this.omschrijving = omschrijving;
        this.tijdstipGemeld = tijdstipGemeld;
        this.status = status;
        this.afgehandeldOp = afgehandeldOp;
    }

    public int getMeldingId()               { return meldingId; }
    public int getStationId()               { return stationId; }
    public String getStationSerienummer()   { return stationSerienummer; }
    public Integer getVaknummer()           { return vaknummer; }
    public Integer getAfgehandeldDoor()     { return afgehandeldDoor; }
    public String getType()                 { return type; }
    public String getOmschrijving()         { return omschrijving; }
    public LocalDateTime getTijdstipGemeld() { return tijdstipGemeld; }
    public String getStatus()               { return status; }
    public void setStatus(String status)    { this.status = status; }
    public LocalDateTime getAfgehandeldOp() { return afgehandeldOp; }
    public void setAfgehandeldOp(LocalDateTime dt) { this.afgehandeldOp = dt; }
    public void setAfgehandeldDoor(Integer id) { this.afgehandeldDoor = id; }
}
