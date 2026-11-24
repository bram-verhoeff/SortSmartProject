package nl.sortsmart.javafx.model;

import java.time.LocalDate;

public class Sorteerstation {
    private int stationId;
    private int campusId;
    private String campusNaam;
    private String serienummer;
    private String locatieOmschrijving;
    private Integer verdieping;
    private LocalDate installatiedatum;
    private String firmwareVersie;
    private String status;

    public Sorteerstation(int stationId, int campusId, String campusNaam, String serienummer,
                          String locatieOmschrijving, Integer verdieping, LocalDate installatiedatum,
                          String firmwareVersie, String status) {
        this.stationId = stationId;
        this.campusId = campusId;
        this.campusNaam = campusNaam;
        this.serienummer = serienummer;
        this.locatieOmschrijving = locatieOmschrijving;
        this.verdieping = verdieping;
        this.installatiedatum = installatiedatum;
        this.firmwareVersie = firmwareVersie;
        this.status = status;
    }

    public int getStationId()               { return stationId; }
    public int getCampusId()                { return campusId; }
    public String getCampusNaam()           { return campusNaam; }
    public String getSerienummer()          { return serienummer; }
    public String getLocatieOmschrijving()  { return locatieOmschrijving; }
    public Integer getVerdieping()          { return verdieping; }
    public LocalDate getInstallatiedatum()  { return installatiedatum; }
    public String getFirmwareVersie()       { return firmwareVersie; }
    public String getStatus()               { return status; }
    public void setStatus(String status)    { this.status = status; }

    @Override
    public String toString() {
        return serienummer + " – " + locatieOmschrijving;
    }
}
