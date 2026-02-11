package Core;

import java.net.URL;

import javafx.scene.image.Image;

public class FileBindings {

    // Cesta k v�choz�mu obr�zku
    public static final String defaultImage = "Images/Lenna.png";

    // Cesta k souboru z rozhran�m aplikace
    public static final URL GUIMain = FileBindings.class.getClassLoader().getResource("graphics/" + "MainWindow" + ".fxml");

    // Ikona aplikace
    public static Image favicon = new Image(FileBindings.class.getClassLoader().getResourceAsStream("favicon.png"));

}
