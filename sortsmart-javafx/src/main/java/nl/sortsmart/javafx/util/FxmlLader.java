package nl.sortsmart.javafx.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.io.IOException;

/**
 * Hulpklasse voor het dynamisch laden van FXML-schermen.
 */
public class FxmlLader {

    private FxmlLader() {}

    /**
     * Laadt een FXML-bestand vanuit het standaard pakket en geeft de root-node terug.
     *
     * @param bestandsnaam  Bestandsnaam zonder pad, bijv. "Dashboard.fxml"
     * @return geladen node
     */
    public static Pane laad(String bestandsnaam) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                FxmlLader.class.getResource("/nl/sortsmart/javafx/fxml/" + bestandsnaam)
        );
        return loader.load();
    }

    /**
     * Laadt een FXML-bestand en geeft de FXMLLoader terug zodat de controller
     * bereikbaar is.
     */
    public static FXMLLoader maakLader(String bestandsnaam) {
        return new FXMLLoader(
                FxmlLader.class.getResource("/nl/sortsmart/javafx/fxml/" + bestandsnaam)
        );
    }
}
