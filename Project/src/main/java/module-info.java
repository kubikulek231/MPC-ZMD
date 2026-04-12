module cz.vutbr.zmd {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive java.desktop;
    requires transitive jama;

    opens core to javafx.graphics;
    opens graphics;

    exports app;
    exports enums;
    exports imageProcessing;
    exports jpeg;
}
