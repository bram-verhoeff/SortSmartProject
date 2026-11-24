package nl.sortsmart.javafx.model;

public class Campus {
    private int campusId;
    private String naam;
    private String adres;
    private String postcode;
    private String plaats;

    public Campus(int campusId, String naam, String adres, String postcode, String plaats) {
        this.campusId = campusId;
        this.naam = naam;
        this.adres = adres;
        this.postcode = postcode;
        this.plaats = plaats;
    }

    public int getCampusId()    { return campusId; }
    public String getNaam()     { return naam; }
    public String getAdres()    { return adres; }
    public String getPostcode() { return postcode; }
    public String getPlaats()   { return plaats; }

    @Override
    public String toString()    { return naam; }
}
