package nl.sortsmart.javafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import nl.sortsmart.javafx.util.DatabaseConnector;

import java.io.IOException;
import java.util.Objects;

public class MainApp extends Application {

    @Override
    public void init() {
        System.out.println("[SortSmart] Applicatie init() gestart...");
    }

    @Override
    public void start(Stage primaryStage) {
        System.out.println("[SortSmart] Applicatie start() aangeroepen...");
        try {
            // Controleer database verbinding bij opstarten
            if (!DatabaseConnector.testVerbinding()) {
                System.out.println("[SortSmart] Geen verbinding met database, toon DbFoutView...");
                toonDatabaseFout(primaryStage);
                return;
            }

            System.out.println("[SortSmart] Verbinding geslaagd, laad hoofdscherm...");
            laadHoofdscherm(primaryStage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void laadHoofdscherm(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/nl/sortsmart/javafx/fxml/MainView.fxml")
        );
        Scene scene = new Scene(loader.load(), 1100, 700);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/nl/sortsmart/javafx/css/style.css")).toExternalForm()
        );
        primaryStage.setTitle("SortSmart – Beheerpaneel");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setOnCloseRequest(e -> {
            System.out.println("[SortSmart] Applicatie wordt afgesloten...");
            System.exit(0);
        });
        primaryStage.setAlwaysOnTop(true);
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.requestFocus();
        primaryStage.setAlwaysOnTop(false);
    }

    private void toonDatabaseFout(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/nl/sortsmart/javafx/fxml/DbFoutView.fxml")
        );
        Scene scene = new Scene(loader.load(), 500, 300);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/nl/sortsmart/javafx/css/style.css")).toExternalForm()
        );
        primaryStage.setTitle("SortSmart – Verbindingsfout");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
