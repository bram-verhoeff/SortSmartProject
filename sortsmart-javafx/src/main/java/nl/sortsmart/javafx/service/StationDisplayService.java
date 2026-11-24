package nl.sortsmart.javafx.service;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;
import nl.sortsmart.javafx.dao.ScanDao;
import nl.sortsmart.javafx.dao.VakDao;
import nl.sortsmart.javafx.model.Afvalfractie;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Random;

/**
 * Serviceklasse die de statusovergangen en schermlogica van het stationsdisplay regelt.
 * Deze klasse is losgekoppeld van de UI, zodat hier later een micro:bit of hardware-aansturing
 * (bijv. via seriële poort / USB) direct op kan worden aangesloten.
 */
public class StationDisplayService {

    private final ObjectProperty<DisplayToestand> toestandProperty =
            new SimpleObjectProperty<>(DisplayToestand.KLAAR);
    private final ObjectProperty<Afvalfractie> actieveFractieProperty =
            new SimpleObjectProperty<>(null);

    private final ScanDao scanDao = new ScanDao();
    private final VakDao vakDao = new VakDao();
    private final Random random = new Random();

    private Timeline actieveTimeline;
    private int actiefStationId = 1; // Standaard station 1

    public StationDisplayService() {}

    public StationDisplayService(int stationId) {
        this.actiefStationId = stationId;
    }

    public ObjectProperty<DisplayToestand> toestandProperty() {
        return toestandProperty;
    }

    public DisplayToestand getToestand() {
        return toestandProperty.get();
    }

    public ObjectProperty<Afvalfractie> actieveFractieProperty() {
        return actieveFractieProperty;
    }

    public Afvalfractie getActieveFractie() {
        return actieveFractieProperty.get();
    }

    public void setActiefStationId(int stationId) {
        this.actiefStationId = stationId;
    }

    public int getActiefStationId() {
        return actiefStationId;
    }

    /**
     * Start de scan-cyclus voor een herkend item van een bepaalde fractie.
     * Flow: Klaar -> Scannen (1.2s) -> Herkend (1.8s) -> Gelukt (3.0s) -> Klaar
     */
    public void startScanVoorFractie(Afvalfractie fractie) {
        startScanVoorFractie(fractie, null, null);
    }

    /**
     * Start de scan-cyclus op basis van een AI-herkenning met specifieke betrouwbaarheid en itemtype.
     */
    public void startScanVoorFractie(Afvalfractie fractie, Integer itemTypeId, BigDecimal betrouwbaarheid) {
        stopLopendeAnimatie();
        actieveFractieProperty.set(fractie);
        toestandProperty.set(DisplayToestand.SCANNEN);

        actieveTimeline = new Timeline(
                new KeyFrame(Duration.millis(1000), e -> {
                    // Stap 2: Herkend
                    toestandProperty.set(DisplayToestand.HERKEND);
                }),
                new KeyFrame(Duration.millis(2600), e -> {
                    // Stap 3: Gelukt & opslaan in database
                    toestandProperty.set(DisplayToestand.GELUKT);
                    slaScanOp(fractie, itemTypeId, betrouwbaarheid);
                }),
                new KeyFrame(Duration.millis(5600), e -> {
                    // Stap 4: Na 3 seconden terug naar Klaar
                    resetNaarKlaar();
                })
        );
        actieveTimeline.play();
    }

    /**
     * Start de cyclus wanneer een item niet herkend kan worden of de betrouwbaarheid te laag is.
     */
    public void startNietHerkend(String reden) {
        stopLopendeAnimatie();
        actieveFractieProperty.set(null);
        toestandProperty.set(DisplayToestand.SCANNEN);

        actieveTimeline = new Timeline(
                new KeyFrame(Duration.millis(1000), e -> {
                    toestandProperty.set(DisplayToestand.NIET_HERKEND);
                    slaFoutScanOp("NietHerkend", null, null);
                }),
                new KeyFrame(Duration.millis(4500), e -> {
                    resetNaarKlaar();
                })
        );
        actieveTimeline.play();
    }

    /**
     * Start de cyclus wanneer er meerdere items tegelijk worden gedetecteerd.
     * Flow: Klaar -> Scannen (1.0s) -> MeerdereItems (3.5s) -> Klaar
     */
    public void startMeerdereItems() {
        stopLopendeAnimatie();
        actieveFractieProperty.set(null);
        toestandProperty.set(DisplayToestand.SCANNEN);

        actieveTimeline = new Timeline(
                new KeyFrame(Duration.millis(1000), e -> {
                    toestandProperty.set(DisplayToestand.MEERDERE_ITEMS);
                    slaFoutScanOp("MeerdereItems", null, null);
                }),
                new KeyFrame(Duration.millis(4500), e -> {
                    resetNaarKlaar();
                })
        );
        actieveTimeline.play();
    }

    /**
     * Start de cyclus wanneer vloeistof wordt gedetecteerd.
     * Flow: Klaar -> Scannen (1.0s) -> Vloeistof (3.5s) -> Klaar
     */
    public void startVloeistofGedetecteerd() {
        stopLopendeAnimatie();
        actieveFractieProperty.set(null);
        toestandProperty.set(DisplayToestand.SCANNEN);

        actieveTimeline = new Timeline(
                new KeyFrame(Duration.millis(1000), e -> {
                    toestandProperty.set(DisplayToestand.VLOEISTOF);
                    BigDecimal betrouwbaarheid = BigDecimal.valueOf(85 + random.nextDouble() * 12)
                            .setScale(2, RoundingMode.HALF_UP);
                    slaFoutScanOp("VloeistofGedetecteerd", null, betrouwbaarheid);
                }),
                new KeyFrame(Duration.millis(4500), e -> {
                    resetNaarKlaar();
                })
        );
        actieveTimeline.play();
    }

    /**
     * Zet de status direct terug op 'Klaar'.
     */
    public void resetNaarKlaar() {
        stopLopendeAnimatie();
        actieveFractieProperty.set(null);
        toestandProperty.set(DisplayToestand.KLAAR);
    }

    private void stopLopendeAnimatie() {
        if (actieveTimeline != null) {
            actieveTimeline.stop();
            actieveTimeline = null;
        }
    }

    private void slaScanOp(Afvalfractie fractie, Integer opgegevenItemTypeId, BigDecimal opgegevenBetrouwbaarheid) {
        new Thread(() -> {
            try {
                // Zoek het bijbehorende itemtype
                Integer itemTypeId = opgegevenItemTypeId != null ? opgegevenItemTypeId :
                        scanDao.getEersteItemtypeIdVoorFractie(fractie.getFractieId());

                BigDecimal betrouwbaarheid = opgegevenBetrouwbaarheid != null ? opgegevenBetrouwbaarheid :
                        BigDecimal.valueOf(92 + random.nextDouble() * 7.5).setScale(2, RoundingMode.HALF_UP);

                // Scan registreren
                scanDao.voegScanToe(actiefStationId, itemTypeId, betrouwbaarheid, "Gesorteerd");

                // Vulgraad van het vak verhogen (bv. +3%)
                vakDao.verhoogVulgraadVoorFractie(actiefStationId, fractie.getFractieId(), 3);
            } catch (SQLException e) {
                System.err.println("[DisplayService] Fout bij opslaan scan/vulgraad: " + e.getMessage());
            }
        }).start();
    }

    private void slaFoutScanOp(String resultaat, Integer itemTypeId, BigDecimal betrouwbaarheid) {
        new Thread(() -> {
            try {
                scanDao.voegScanToe(actiefStationId, itemTypeId, betrouwbaarheid, resultaat);
            } catch (SQLException e) {
                System.err.println("[DisplayService] Fout bij opslaan scan: " + e.getMessage());
            }
        }).start();
    }
}
