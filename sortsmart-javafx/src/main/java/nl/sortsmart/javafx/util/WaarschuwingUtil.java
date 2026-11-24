package nl.sortsmart.javafx.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * Hulpklasse voor het tonen van foutmeldingen en bevestigingsdialogen.
 */
public class WaarschuwingUtil {

    private WaarschuwingUtil() {}

    public static void toonFout(String titel, String bericht) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(titel);
        alert.setHeaderText(null);
        alert.setContentText(bericht);
        alert.showAndWait();
    }

    public static void toonInfo(String titel, String bericht) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(titel);
        alert.setHeaderText(null);
        alert.setContentText(bericht);
        alert.showAndWait();
    }

    public static void toonWaarschuwing(String titel, String bericht) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle(titel);
        alert.setHeaderText(null);
        alert.setContentText(bericht);
        alert.showAndWait();
    }
}
