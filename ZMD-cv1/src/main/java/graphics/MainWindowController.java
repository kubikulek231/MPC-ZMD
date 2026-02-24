package graphics;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import core.FileBindings;
import core.Helper;
import enums.SamplingType;
import enums.TransformType;
import imageProcessing.ProcessImage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Pair;

public class MainWindowController implements Initializable {

    private ProcessImage process;

    @FXML Button buttonInverseQuantize;
    @FXML Button buttonInverseToRGB;
    @FXML Button buttonInverseSample;
    @FXML Button buttonInverseTransform;
    @FXML Button buttonQuantize;
    @FXML Button buttonSample;
    @FXML Button buttonToYCbCr;
    @FXML Button buttonTransform;

    @FXML TextField qualityMSE;
    @FXML TextField qualityPSNR;

    @FXML Slider quantizeQuality;
    @FXML TextField quantizeQualityField;

    @FXML CheckBox shadesOfGrey;
    @FXML CheckBox showSteps;

    @FXML Spinner<Integer> transformBlock;
    @FXML ComboBox<TransformType> transformType;
    @FXML ComboBox<SamplingType>  sampling;

    // ===== Initialization =====

    /** Inicializace okna, nastavení výchozích hodnot. Naplnění prvků v rozhraní. */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        sampling.getItems().setAll(SamplingType.values());
        transformType.getItems().setAll(TransformType.values());

        sampling.getSelectionModel().select(SamplingType.S_4_4_4);
        transformType.getSelectionModel().select(TransformType.DCT);
        quantizeQuality.setValue(50);

        ObservableList<Integer> blocks = FXCollections.observableArrayList(2, 4, 8, 16, 32, 64, 128, 256, 512);
        SpinnerValueFactory<Integer> spinnerValues = new SpinnerValueFactory.ListSpinnerValueFactory<>(blocks);
        spinnerValues.setValue(8);
        transformBlock.setValueFactory(spinnerValues);

        quantizeQualityField.setTextFormatter(new TextFormatter<>(Helper.NUMBER_FORMATTER));
        quantizeQualityField.textProperty().bindBidirectional(quantizeQuality.valueProperty(), NumberFormat.getIntegerInstance());

        BufferedImage defaultImage = Dialogs.loadImageFromPath(FileBindings.DEFAULT_IMAGE);
        this.process = new ProcessImage(defaultImage);
    }

    // ===== Window Controls =====

    public void close() {
        Stage stage = (Stage) buttonSample.getScene().getWindow();
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public void closeWindows() {
        Dialogs.closeAllWindows();
    }

    // ===== Image Loading =====

    /** Show the unmodified original image in a pinned window. */
    public void showOriginal() {
        File f = new File(FileBindings.DEFAULT_IMAGE);
        try {
            Dialogs.showImageInWindow(ImageIO.read(f), "Original", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Open a file-chooser and load a new working image. */
    public void changeImage() {
        File selectedFile = Dialogs.openFile();
        if (selectedFile == null) return;
        process.loadImage(selectedFile.getAbsolutePath());
    }

    // ===== Reset =====

    /** Restore all modified channels back to the original state. */
    public void reset() {
        process.resetModified();
        qualityMSE.clear();
        qualityPSNR.clear();
    }

    // ===== RGB Channel Viewers =====

    /** Show the current (modified) image reconstructed from its RGB channels. */
    public void showRGBModified() {
        Dialogs.showImageInWindow(process.getImageFromRGB(), "RGB (modified)");
    }

    public void showRedOriginal() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.originalRed, ProcessImage.ColorType.RED, shadesOfGrey.isSelected()),
                "Red (original)");
    }

    public void showRedModified() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.modifiedRed, ProcessImage.ColorType.RED, shadesOfGrey.isSelected()),
                "Red (modified)");
    }

    public void showGreenOriginal() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.originalGreen, ProcessImage.ColorType.GREEN, shadesOfGrey.isSelected()),
                "Green (original)");
    }

    public void showGreenModified() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.modifiedGreen, ProcessImage.ColorType.GREEN, shadesOfGrey.isSelected()),
                "Green (modified)");
    }

    public void showBlueOriginal() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.originalBlue, ProcessImage.ColorType.BLUE, shadesOfGrey.isSelected()),
                "Blue (original)");
    }

    public void showBlueModified() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.modifiedBlue, ProcessImage.ColorType.BLUE, shadesOfGrey.isSelected()),
                "Blue (modified)");
    }

    // ===== YCbCr Channel Viewers =====

    public void showYOriginal() {
        if (process.originalY == null) return;
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.originalY), "Y (original)");
    }

    public void showYModified() {
        if (process.modifiedY == null) return;
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.modifiedY), "Y (modified)");
    }

    public void showCbOriginal() {
        if (process.originalCb == null) return;
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.originalCb), "Cb (original)");
    }

    public void showCbModified() {
        if (process.modifiedCb == null) return;
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.modifiedCb), "Cb (modified)");
    }

    public void showCrOriginal() {
        if (process.originalCr == null) return;
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.originalCr), "Cr (original)");
    }

    public void showCrModified() {
        if (process.modifiedCr == null) return;
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.modifiedCr), "Cr (modified)");
    }

    // ===== Processing Pipeline =====

    /** Convert the modified RGB image to YCbCr colour space. */
    public void convertToYCbCr() {
        process.convertToYCbCr();
        if (showSteps.isSelected()) {
            Dialogs.showMultipleImageInWindow("YCbCr channels", false, true,
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedY),  "Y"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedCb), "Cb"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedCr), "Cr"));
        }
    }

    /** Convert the modified YCbCr channels back to RGB. */
    public void convertToRGB() {
        if (process.modifiedY == null) return;
        process.convertToRGB();
        if (showSteps.isSelected()) {
            Dialogs.showImageInWindow(process.getImageFromRGB(), "RGB (after inverse)");
        }
    }

    /** Apply chroma subsampling to the modified Cb and Cr channels. */
    public void sample() {
        if (process.modifiedCb == null) return;
        SamplingType type = sampling.getSelectionModel().getSelectedItem();
        process.applySampling(type);
        if (showSteps.isSelected()) {
            Dialogs.showMultipleImageInWindow("After sampling (" + type + ")", false, true,
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedCb), "Cb"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedCr), "Cr"));
        }
    }

    /** Inverse chroma subsampling (bilinear interpolation). */
    public void inverseSample() {
        if (process.modifiedCb == null) return;
        process.applyInverseSampling(sampling.getSelectionModel().getSelectedItem());
    }

    /** Apply the selected block transform (DCT / WHT) to the YCbCr channels. */
    public void transform() {
        if (process.modifiedY == null) return;
        process.applyTransform(transformType.getValue(), transformBlock.getValue());
    }

    /** Inverse block transform on the YCbCr channels. */
    public void inverseTransform() {
        if (process.modifiedY == null) return;
        process.applyInverseTransform(transformType.getValue(), transformBlock.getValue());
        if (showSteps.isSelected()) {
            Dialogs.showMultipleImageInWindow("After inverse transform", false, true,
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedY),  "Y"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedCb), "Cb"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.modifiedCr), "Cr"));
        }
    }

    /** Quantize the transform coefficients using the selected quality setting. */
    public void quantize() {
        if (process.modifiedY == null) return;
        process.applyQuantization((int) quantizeQuality.getValue(), transformBlock.getValue());
    }

    /** Dequantize the transform coefficients. */
    public void inverseQuantize() {
        if (process.modifiedY == null) return;
        process.applyInverseQuantization((int) quantizeQuality.getValue(), transformBlock.getValue());
    }

    // ===== Quality Metrics =====

    /** Calculate and display MSE and PSNR between the original and modified RGB images. */
    public void countQuality() {
        double mse  = process.calculateMSE();
        double psnr = process.calculatePSNR();
        qualityMSE.setText(String.format("%.4f", mse));
        qualityPSNR.setText(Double.isInfinite(psnr)
                ? "\u221E dB"
                : String.format("%.2f dB", psnr));
    }
}


