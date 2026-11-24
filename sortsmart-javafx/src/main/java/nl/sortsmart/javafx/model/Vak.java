package nl.sortsmart.javafx.model;

public class Vak {
    private int stationId;
    private int vaknummer;
    private int fractieId;
    private String fractieNaam;
    private String kleurcode;
    private int capaciteitLiter;
    private int vulgraadProcent;

    public Vak(int stationId, int vaknummer, int fractieId, String fractieNaam,
               String kleurcode, int capaciteitLiter, int vulgraadProcent) {
        this.stationId = stationId;
        this.vaknummer = vaknummer;
        this.fractieId = fractieId;
        this.fractieNaam = fractieNaam;
        this.kleurcode = kleurcode;
        this.capaciteitLiter = capaciteitLiter;
        this.vulgraadProcent = vulgraadProcent;
    }

    public int getStationId()        { return stationId; }
    public int getVaknummer()        { return vaknummer; }
    public int getFractieId()        { return fractieId; }
    public String getFractieNaam()   { return fractieNaam; }
    public String getKleurcode()     { return kleurcode; }
    public int getCapaciteitLiter()  { return capaciteitLiter; }
    public int getVulgraadProcent()  { return vulgraadProcent; }
    public void setVulgraadProcent(int v) { this.vulgraadProcent = v; }
}
