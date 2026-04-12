package graphics;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ResourceBundle;

import core.FileBindings;
import core.Helper;
import enums.QualityType;
import enums.SamplingType;
import enums.TransformType;
import imageProcessing.ProcessImage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
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
import javafx.scene.text.Text;
import javafx.util.Pair;

public class MainWindowController implements Initializable {

    private ProcessImage process;
    private boolean ycbcrActive = false;
    private boolean sampled = false;
    private boolean transformed = false;
    private boolean quantized = false;
    private boolean rgbReconstructed = false;

    @FXML Button buttonInverseQuantize;
    @FXML Button buttonInverseToRGB;
    @FXML Button buttonInverseSample;
    @FXML Button buttonInverseTransform;
    @FXML Button buttonQuantize;
    @FXML Button buttonCountPSNR;
    @FXML Button buttonSample;
    @FXML Button buttonToYCbCr;
    @FXML Button buttonTransform;

    @FXML TextField qualityMSE;
    @FXML TextField qualityMAE;
    @FXML TextField qualitySAE;
    @FXML TextField qualityPSNR;
    @FXML Text workflowStatus;

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
        updateWorkflowControls();
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
        Dialogs.showImageInWindow(process.originalImage, "Original", true);
    }

    /** Open a file-chooser and load a new working image. */
    public void changeImage() {
        File selectedFile = Dialogs.openFile();
        if (selectedFile == null) return;
        process.loadImage(selectedFile.getAbsolutePath());
        resetWorkflow();
    }

    public void useDefaultImage() {
        process.loadImage(FileBindings.DEFAULT_IMAGE);
        resetWorkflow();
    }

    // ===== Reset =====

    /** Restore all modified channels back to the original state. */
    public void reset() {
        process.resetModified();
        clearPSNRFields();
        resetWorkflow();
    }

    private void clearPSNRFields() {
        qualityMSE.clear();
        qualityMAE.clear();
        qualitySAE.clear();
        qualityPSNR.clear();
    }


    // ===== RGB Channel Viewers =====

    /** Show the current (modified) image reconstructed from its RGB channels. */
    public void showRGBModified() {
        Dialogs.showImageInWindow(process.getImageFromRGB(), "RGB (modified)");
    }

    public void showRedOriginal() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.getOriginalRgb().getRed(), ProcessImage.ColorType.RED, shadesOfGrey.isSelected()),
                "Red (original)");
    }

    public void showRedModified() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.getWorkingRgb().getRed(), ProcessImage.ColorType.RED, shadesOfGrey.isSelected()),
                "Red (modified)");
    }

    public void showGreenOriginal() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.getOriginalRgb().getGreen(), ProcessImage.ColorType.GREEN, shadesOfGrey.isSelected()),
                "Green (original)");
    }

    public void showGreenModified() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.getWorkingRgb().getGreen(), ProcessImage.ColorType.GREEN, shadesOfGrey.isSelected()),
                "Green (modified)");
    }

    public void showBlueOriginal() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.getOriginalRgb().getBlue(), ProcessImage.ColorType.BLUE, shadesOfGrey.isSelected()),
                "Blue (original)");
    }

    public void showBlueModified() {
        Dialogs.showImageInWindow(
                process.showOneColorImageFromRGB(process.getWorkingRgb().getBlue(), ProcessImage.ColorType.BLUE, shadesOfGrey.isSelected()),
                "Blue (modified)");
    }

    // ===== YCbCr Channel Viewers =====

    public void showYOriginal() {
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.getOriginalYCbCr().getY()), "Y (original)");
    }

    public void showYModified() {
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getY()), "Y (modified)");
    }

    public void showCbOriginal() {
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.getOriginalYCbCr().getCb()), "Cb (original)");
    }

    public void showCbModified() {
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCb()), "Cb (modified)");
    }

    public void showCrOriginal() {
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.getOriginalYCbCr().getCr()), "Cr (original)");
    }

    public void showCrModified() {
        Dialogs.showImageInWindow(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCr()), "Cr (modified)");
    }

    // ===== Processing Pipeline =====

    /** Convert the modified RGB image to YCbCr colour space. */
    public void convertToYCbCr() {
        ycbcrActive = true;
        sampled = false;
        transformed = false;
        quantized = false;
        process.convertToYCbCr();
        updateWorkflowControls();
        if (showSteps.isSelected()) {
            Dialogs.showMultipleImageInWindow("YCbCr channels", false, true,
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getY()),  "Y"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCb()), "Cb"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCr()), "Cr"));
        }
    }

    /** Convert the modified YCbCr channels back to RGB. */
    public void convertToRGB() {
        if (!ycbcrActive || sampled || transformed || quantized) return;
        ycbcrActive = false;
        sampled = false;
        transformed = false;
        quantized = false;
        process.convertToRGB();
        rgbReconstructed = true;
        updateWorkflowControls();
        if (showSteps.isSelected()) {
            Dialogs.showImageInWindow(process.getImageFromRGB(), "RGB (after inverse)");
        }
    }

    /** Apply chroma subsampling to the modified Cb and Cr channels. */
    public void sample() {
        if (!ycbcrActive || sampled || transformed || quantized) return;
        SamplingType type = sampling.getSelectionModel().getSelectedItem();
        process.applySampling(type);
        sampled = true;
        updateWorkflowControls();
        if (showSteps.isSelected()) {
            Dialogs.showMultipleImageInWindow("After sampling (" + type + ")", false, true,
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCb()), "Cb"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCr()), "Cr"));
        }
    }

    /** Inverse chroma subsampling (bilinear interpolation). */
    public void inverseSample() {
        if (!ycbcrActive || !sampled || transformed || quantized) return;
        process.applyInverseSampling(sampling.getSelectionModel().getSelectedItem());
        sampled = false;
        updateWorkflowControls();
    }

    /** Apply the selected block transform (DCT / WHT) to the YCbCr channels. */
    public void transform() {
        if (!ycbcrActive || transformed || quantized) return;
        process.applyTransform(transformType.getValue(), transformBlock.getValue());
        transformed = true;
        updateWorkflowControls();
    }

    /** Inverse block transform on the YCbCr channels. */
    public void inverseTransform() {
        if (!ycbcrActive || !transformed || quantized) return;
        process.applyInverseTransform(transformType.getValue(), transformBlock.getValue());
        transformed = false;
        updateWorkflowControls();
        if (showSteps.isSelected()) {
            Dialogs.showMultipleImageInWindow("After inverse transform", false, true,
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getY()),  "Y"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCb()), "Cb"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCr()), "Cr"));
        }
    }

    /** Quantize the transform coefficients using the selected quality setting. */
    public void quantize() {
        if (!ycbcrActive || !transformed || quantized) return;
        process.applyQuantization((int) quantizeQuality.getValue(), transformBlock.getValue());
        quantized = true;
        updateWorkflowControls();
    }

    /** Dequantize the transform coefficients. */
    public void inverseQuantize() {
        if (!ycbcrActive || !quantized) return;
        process.applyInverseQuantization((int) quantizeQuality.getValue(), transformBlock.getValue());
        quantized = false;
        updateWorkflowControls();
    }

    // ===== Quality Metrics =====

    /** Calculate and display MSE, MAE, SAE, and PSNR for the RGB image. */
    public void countPSNR() {
        if (ycbcrActive) {
            new Alert(AlertType.INFORMATION, "Finish the YCbCr workflow and convert back to RGB first.").showAndWait();
            return;
        }
        if (!rgbReconstructed) {
            new Alert(AlertType.INFORMATION, "Run RGB -> YCbCr processing and convert back to RGB first.").showAndWait();
            return;
        }
        try {
            double[] metrics = process.calculateMetrics(QualityType.RGB); // [mse, mae, sae, psnr]
            qualityMSE.setText(String.format("%.4f", metrics[0]));
            qualityMAE.setText(String.format("%.4f", metrics[1]));
            qualitySAE.setText(String.format("%.2f",  metrics[2]));
            qualityPSNR.setText(Double.isInfinite(metrics[3])
                    ? "\u221E dB"
                    : String.format("%.2f dB", metrics[3]));
        } catch (IllegalStateException e) {
            new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private void resetWorkflow() {
        ycbcrActive = false;
        sampled = false;
        transformed = false;
        quantized = false;
        rgbReconstructed = false;
        clearPSNRFields();
        updateWorkflowControls();
    }

    private void updateWorkflowControls() {
        buttonToYCbCr.setDisable(ycbcrActive);

        buttonSample.setDisable(!ycbcrActive || sampled || transformed || quantized);
        buttonTransform.setDisable(!ycbcrActive || transformed || quantized);
        buttonQuantize.setDisable(!ycbcrActive || !transformed || quantized);

        // Lock parameters once a corresponding forward step is active,
        // so inverse operations always use matching settings.
        sampling.setDisable(ycbcrActive && (sampled || transformed || quantized));
        transformType.setDisable(ycbcrActive && (transformed || quantized));
        transformBlock.setDisable(ycbcrActive && (transformed || quantized));
        quantizeQuality.setDisable(ycbcrActive && quantized);
        quantizeQualityField.setDisable(ycbcrActive && quantized);

        buttonInverseQuantize.setDisable(!ycbcrActive || !quantized);
        buttonInverseTransform.setDisable(!ycbcrActive || !transformed || quantized);
        buttonInverseSample.setDisable(!ycbcrActive || !sampled || transformed || quantized);
        buttonInverseToRGB.setDisable(!ycbcrActive || sampled || transformed || quantized);
        buttonCountPSNR.setDisable(ycbcrActive || !rgbReconstructed);

        if (workflowStatus == null) return;

        if (!ycbcrActive) {
            workflowStatus.setText("Start here: open an image or use the default one, then convert the working image to YCbCr.");
            return;
        }
        if (quantized) {
            workflowStatus.setText("Quantization is active. Dequantize before you continue back to RGB.");
            return;
        }
        if (transformed) {
            workflowStatus.setText("Transform is active. Apply the inverse transform before converting back to RGB.");
            return;
        }
        if (sampled) {
            workflowStatus.setText("Downsampling is active. Restore chroma sampling before converting back to RGB.");
            return;
        }

        workflowStatus.setText("YCbCr is ready. You can now downsample, transform, quantize, inspect channels, or convert back to RGB.");
    }
}