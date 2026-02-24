module cz.vutbr.zmd {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires jama;

    opens core to javafx.graphics;
    opens graphics;

    exports app;
}
