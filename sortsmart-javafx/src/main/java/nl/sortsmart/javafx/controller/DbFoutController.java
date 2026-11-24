package nl.sortsmart.javafx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller voor het databasefoutscherm (getoond als de db niet bereikbaar is bij opstarten).
 */
public class DbFoutController {

    @FXML private Button btnOpnieuw;

    @FXML
    private void opnieuwProberen() {
        try {
            if (nl.sortsmart.javafx.util.DatabaseConnector.testVerbinding()) {
                Stage huidig = (Stage) btnOpnieuw.getScene().getWindow();
                nl.sortsmart.javafx.MainApp app = new nl.sortsmart.javafx.MainApp();
                app.start(huidig);
            } else {
                nl.sortsmart.javafx.util.WaarschuwingUtil.toonFout(
                        "Geen verbinding",
                        "De database is nog steeds niet bereikbaar.\n" +
                        "Controleer of MySQL actief is en de gegevens in db.properties kloppen."
                );
            }
        } catch (Exception e) {
            nl.sortsmart.javafx.util.WaarschuwingUtil.toonFout("Fout", e.getMessage());
        }
    }

    @FXML
    private void afsluiten() {
        Stage stage = (Stage) btnOpnieuw.getScene().getWindow();
        stage.close();
    }
}
