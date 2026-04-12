package core;

import java.net.URL;

import javafx.scene.image.Image;

public class FileBindings {

    // Cesta k výchozímu obrázku
    public static final String DEFAULT_IMAGE = "Images/Lenna.png";

    // Cesta k souboru z rozhraním aplikace
    public static final URL GUIMain = FileBindings.class.getClassLoader().getResource("graphics/" + "MainWindow" + ".fxml");

    // Ikona aplikace
    public static Image favicon = new Image(FileBindings.class.getClassLoader().getResourceAsStream("favicon.png"));

}
