package nl.sortsmart.javafx.model;

/**
 * Model voor een afvalfractie (Papier, PMD, Rest, Etensresten).
 */
public class Afvalfractie {
    private int fractieId;
    private String naam;
    private String kleurcode;
    private String omschrijving;

    public Afvalfractie(int fractieId, String naam, String kleurcode, String omschrijving) {
        this.fractieId = fractieId;
        this.naam = naam;
        this.kleurcode = kleurcode;
        this.omschrijving = omschrijving;
    }

    public int getFractieId()        { return fractieId; }
    public String getNaam()          { return naam; }
    public String getKleurcode()     { return kleurcode; }
    public String getOmschrijving()  { return omschrijving; }

    @Override
    public String toString() {
        return naam;
    }
}
