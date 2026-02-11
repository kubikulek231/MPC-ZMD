package Core;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Hlavní okno aplikace. Spustí hlavní okno. Inicializace JavaFX
 *
 * @author David Kohout
 */
public class JFXMain extends Application {
    private static Stage primaryStage;
    public static Scene mainScene;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        FXMLLoader fl = new FXMLLoader(FileBindings.GUIMain); // Načtení okna z fxml souboru
        Parent root = fl.load();

        mainScene = new Scene(root); // Vytvoření scény z fxml souboru
        primaryStage.setScene(mainScene); // Vložení scény do okna

        primaryStage.setTitle("JPEG: Prijmeni VUTID"); // Titulek okna, nastavte svoje
        primaryStage.getIcons().add(FileBindings.favicon); // Přidání ikony aplikace
        primaryStage.show(); // Zobrazí rozhraní

        // Není nutné, umožňuje provést kód po stisku X (př. potvrzení ukončení)
        primaryStage.setOnCloseRequest((e) -> {
            Platform.exit();
            System.exit(0);
        });
    }

    // Nutné mít launch uvnitř main metody.
    // Bohužel nejde spustit přímo z této třídy.
    public static void main(String[] args) {
        launch(args);
    }
}
