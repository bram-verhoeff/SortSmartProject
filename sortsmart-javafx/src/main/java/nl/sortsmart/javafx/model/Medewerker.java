package nl.sortsmart.javafx.model;

public class Medewerker {
    private int medewerkerId;
    private String voornaam;
    private String tussenvoegsel;
    private String achternaam;
    private String email;
    private String rol;

    public Medewerker(int medewerkerId, String voornaam, String tussenvoegsel,
                      String achternaam, String email, String rol) {
        this.medewerkerId = medewerkerId;
        this.voornaam = voornaam;
        this.tussenvoegsel = tussenvoegsel;
        this.achternaam = achternaam;
        this.email = email;
        this.rol = rol;
    }

    public int getMedewerkerId()    { return medewerkerId; }
    public String getVoornaam()     { return voornaam; }
    public String getTussenvoegsel() { return tussenvoegsel; }
    public String getAchternaam()   { return achternaam; }
    public String getEmail()        { return email; }
    public String getRol()          { return rol; }

    public String getVolledigeNaam() {
        if (tussenvoegsel != null && !tussenvoegsel.isBlank()) {
            return voornaam + " " + tussenvoegsel + " " + achternaam;
        }
        return voornaam + " " + achternaam;
    }

    @Override
    public String toString() {
        return getVolledigeNaam();
    }
}
