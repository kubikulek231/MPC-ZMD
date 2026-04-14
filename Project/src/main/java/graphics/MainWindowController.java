package graphics;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ResourceBundle;

import Jama.Matrix;
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
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Pair;
import watermark.DctWatermark;
import watermark.LsbWatermark;
import watermark.PatchworkWatermark;
import watermark.SpreadSpectrumWatermark;
import watermark.WatermarkAttacks;

public class MainWindowController implements Initializable {

    private ProcessImage process;
    private boolean ycbcrActive = false;
    private boolean sampled = false;
    private boolean transformed = false;
    private boolean quantized = false;
    private boolean rgbReconstructed = false;

    // Watermark state
    private BufferedImage watermarkImage = null;
    private BufferedImage extractedWatermark = null;
    private boolean lsbEmbedded = false;
    private boolean dctEmbedded = false;
    private boolean ssEmbedded = false;
    private boolean pwEmbedded = false;

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

    // Watermark controls
    @FXML Label wmStatusLabel;
    @FXML CheckBox wmMultiInsert;
    @FXML ComboBox<String> wmLsbChannel;
    @FXML Spinner<Integer> wmLsbBitPlane;
    @FXML TextField wmLsbKey;
    @FXML Spinner<Integer> wmLsbStrength;
    @FXML Spinner<Integer> wmDctBlock;
    @FXML Spinner<Integer> wmDctU1;
    @FXML Spinner<Integer> wmDctV1;
    @FXML Spinner<Integer> wmDctU2;
    @FXML Spinner<Integer> wmDctV2;
    @FXML TextField wmDctDepth;
    @FXML TextField wmSsAlpha;
    @FXML TextField wmSsKey;
    @FXML TextField wmPwDelta;
    @FXML TextField wmPwKey;
    @FXML Slider attackJpegQuality;
    @FXML Text wmWorkflowStatus;

    // Watermark buttons
    @FXML Button btnLoadWatermark;
    @FXML Button btnShowWatermark;
    @FXML Button btnShowExtracted;
    @FXML Button btnLsbEmbed;
    @FXML Button btnLsbExtract;
    @FXML Button btnDctEmbed;
    @FXML Button btnDctExtract;
    @FXML Button btnSsEmbed;
    @FXML Button btnSsExtract;
    @FXML Button btnPwEmbed;
    @FXML Button btnPwExtract;
    @FXML Button btnAttackJpeg;
    @FXML Button btnAttackPng;
    @FXML Button btnAttackRotate45;
    @FXML Button btnAttackRotate90;
    @FXML Button btnAttackResize75;
    @FXML Button btnAttackResize50;
    @FXML Button btnAttackMirror;
    @FXML Button btnAttackCrop;

    @FXML Button btnUndoChanges;
    @FXML CheckBox shadesOfGrey;
    @FXML CheckBox showSteps;
    @FXML CheckBox guidedMode;

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

        // Watermark control defaults.
        wmLsbChannel.getItems().setAll("Y", "Cb", "Cr");
        wmLsbChannel.getSelectionModel().select("Y");
        wmLsbBitPlane.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 7, 0));
        wmLsbStrength.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 255, 0));

        ObservableList<Integer> dctBlocks = FXCollections.observableArrayList(2, 4, 8, 16, 32, 64);
        SpinnerValueFactory<Integer> dctBlockSvf = new SpinnerValueFactory.ListSpinnerValueFactory<>(dctBlocks);
        dctBlockSvf.setValue(8);
        wmDctBlock.setValueFactory(dctBlockSvf);

        wmDctU1.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 63, 3));
        wmDctV1.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 63, 1));
        wmDctU2.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 63, 4));
        wmDctV2.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 63, 1));

        // Make editable spinners commit typed values on focus loss.
        for (Spinner<?> s : new Spinner<?>[]{ wmLsbBitPlane, wmLsbStrength, wmDctU1, wmDctV1, wmDctU2, wmDctV2 }) {
            s.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) s.commitValue();
            });
        }

        updateWorkflowControls();
        updateWatermarkControls();
    }

    // Called when the guided-mode checkbox is toggled.
    public void onGuidedModeToggle() {
        updateWorkflowControls();
        updateWatermarkControls();
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
        updateWatermarkControls();
        if (showSteps.isSelected()) {
            Dialogs.showMultipleImageInWindow("YCbCr channels", false, true,
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getY()),  "Y"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCb()), "Cb"),
                    new Pair<>(process.showOneColorImageFromYCbCr(process.getWorkingYCbCr().getCr()), "Cr"));
        }
    }

    /** Convert the modified YCbCr channels back to RGB. */
    public void convertToRGB() {
        if (isGuided() && (!ycbcrActive || sampled || transformed || quantized)) return;
        ycbcrActive = false;
        sampled = false;
        transformed = false;
        quantized = false;
        process.convertToRGB();
        rgbReconstructed = true;
        updateWorkflowControls();
        updateWatermarkControls();
        if (showSteps.isSelected()) {
            Dialogs.showImageInWindow(process.getImageFromRGB(), "RGB (after inverse)");
        }
    }

    /** Apply chroma subsampling to the modified Cb and Cr channels. */
    public void sample() {
        if (isGuided() && (!ycbcrActive || sampled || transformed || quantized)) return;
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
        if (isGuided() && (!ycbcrActive || !sampled || transformed || quantized)) return;
        process.applyInverseSampling(sampling.getSelectionModel().getSelectedItem());
        sampled = false;
        updateWorkflowControls();
    }

    /** Apply the selected block transform (DCT / WHT) to the YCbCr channels. */
    public void transform() {
        if (isGuided() && (!ycbcrActive || transformed || quantized)) return;
        process.applyTransform(transformType.getValue(), transformBlock.getValue());
        transformed = true;
        updateWorkflowControls();
    }

    /** Inverse block transform on the YCbCr channels. */
    public void inverseTransform() {
        if (isGuided() && (!ycbcrActive || !transformed || quantized)) return;
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
        if (isGuided() && (!ycbcrActive || !transformed || quantized)) return;
        process.applyQuantization((int) quantizeQuality.getValue(), transformBlock.getValue());
        quantized = true;
        updateWorkflowControls();
    }

    /** Dequantize the transform coefficients. */
    public void inverseQuantize() {
        if (isGuided() && (!ycbcrActive || !quantized)) return;
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
        updateUndoButton();
        updateWorkflowControls();
        updateWatermarkControls();
    }

    private void updateUndoButton() {
        if (btnUndoChanges != null) {
            boolean hasChanges = ycbcrActive || sampled || transformed || quantized
                    || lsbEmbedded || dctEmbedded || ssEmbedded || pwEmbedded;
            btnUndoChanges.setDisable(!hasChanges);
        }
    }

    private boolean isGuided() {
        return guidedMode == null || guidedMode.isSelected();
    }

    private void updateWorkflowControls() {
        updateUndoButton();
        if (!isGuided()) {
            // unguided -- everything is clickable
            buttonToYCbCr.setDisable(false);
            buttonSample.setDisable(false);
            buttonTransform.setDisable(false);
            buttonQuantize.setDisable(false);
            sampling.setDisable(false);
            transformType.setDisable(false);
            transformBlock.setDisable(false);
            quantizeQuality.setDisable(false);
            quantizeQualityField.setDisable(false);
            buttonInverseQuantize.setDisable(false);
            buttonInverseTransform.setDisable(false);
            buttonInverseSample.setDisable(false);
            buttonInverseToRGB.setDisable(false);
            buttonCountPSNR.setDisable(false);
            if (workflowStatus != null) {
                workflowStatus.setText("Guided mode OFF -- all buttons are unlocked. Click in any order.");
            }
            return;
        }

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

    private void updateWatermarkControls() {
        updateUndoButton();
        boolean wmLoaded = watermarkImage != null;

        if (!isGuided()) {
            // unguided -- everything unlocked (except show buttons that need data)
            btnShowWatermark.setDisable(!wmLoaded);
            btnShowExtracted.setDisable(extractedWatermark == null);
            btnLsbEmbed.setDisable(!wmLoaded);
            btnLsbExtract.setDisable(!wmLoaded);
            btnDctEmbed.setDisable(!wmLoaded);
            btnDctExtract.setDisable(!wmLoaded);
            btnSsEmbed.setDisable(!wmLoaded);
            btnSsExtract.setDisable(!wmLoaded);
            btnPwEmbed.setDisable(!wmLoaded);
            btnPwExtract.setDisable(!wmLoaded);
            btnAttackJpeg.setDisable(false);
            btnAttackPng.setDisable(false);
            btnAttackRotate45.setDisable(false);
            btnAttackRotate90.setDisable(false);
            btnAttackResize75.setDisable(false);
            btnAttackResize50.setDisable(false);
            btnAttackMirror.setDisable(false);
            btnAttackCrop.setDisable(false);
            if (wmWorkflowStatus != null) {
                wmWorkflowStatus.setText("Guided mode OFF -- all watermark buttons unlocked.");
            }
            return;
        }

        boolean canEmbed = wmLoaded && ycbcrActive && !sampled && !transformed && !quantized;
        boolean canExtract = wmLoaded && ycbcrActive && !sampled && !transformed && !quantized;
        boolean wmEmbedded = lsbEmbedded || dctEmbedded || ssEmbedded || pwEmbedded;

        btnShowWatermark.setDisable(!wmLoaded);
        btnShowExtracted.setDisable(extractedWatermark == null);

        btnLsbEmbed.setDisable(!canEmbed);
        btnLsbExtract.setDisable(!canExtract);
        btnDctEmbed.setDisable(!canEmbed);
        btnDctExtract.setDisable(!canExtract);
        btnSsEmbed.setDisable(!canEmbed);
        btnSsExtract.setDisable(!canExtract);
        btnPwEmbed.setDisable(!canEmbed);
        btnPwExtract.setDisable(!canExtract);

        boolean canAttack = wmEmbedded && !ycbcrActive;
        btnAttackJpeg.setDisable(!canAttack);
        btnAttackPng.setDisable(!canAttack);
        btnAttackRotate45.setDisable(!canAttack);
        btnAttackRotate90.setDisable(!canAttack);
        btnAttackResize75.setDisable(!canAttack);
        btnAttackResize50.setDisable(!canAttack);
        btnAttackMirror.setDisable(!canAttack);
        btnAttackCrop.setDisable(!canAttack);

        if (wmWorkflowStatus == null) return;

        if (!wmLoaded) {
            wmWorkflowStatus.setText("Step 1: Load an image above, convert to YCbCr. Then load a watermark image.");
        } else if (!ycbcrActive) {
            wmWorkflowStatus.setText("Step 2: Convert the working image to YCbCr (above) before embedding.");
        } else if (sampled || transformed || quantized) {
            wmWorkflowStatus.setText("Undo sampling/transform/quantization first \u2014 watermark operates on raw YCbCr channels.");
        } else if (!wmEmbedded) {
            wmWorkflowStatus.setText("Step 3: Embed a watermark using LSB or DCT, then convert back to RGB.");
        } else if (ycbcrActive) {
            wmWorkflowStatus.setText("Step 4: Convert back to RGB (above) to see the watermarked image and run attacks.");
        } else {
            wmWorkflowStatus.setText("Step 5: Apply attacks below, then convert attacked image to YCbCr and extract the watermark.");
        }
    }

    // ===== Watermark Handlers =====

    public void loadWatermark() {
        File file = Dialogs.openFile();
        if (file == null) return;
        watermarkImage = Dialogs.loadImageFromPath(file);
        wmStatusLabel.setText(watermarkImage.getWidth() + "x" + watermarkImage.getHeight() + " loaded");
        updateWatermarkControls();
    }

    public void showWatermark() {
        if (watermarkImage == null) {
            new Alert(AlertType.INFORMATION, "Load a watermark image first.").showAndWait();
            return;
        }
        Dialogs.showImageInWindow(watermarkImage, "Watermark");
    }

    public void showExtractedWatermark() {
        if (extractedWatermark == null) {
            new Alert(AlertType.INFORMATION, "Extract a watermark first.").showAndWait();
            return;
        }
        Dialogs.showImageInWindow(extractedWatermark, "Extracted Watermark");
    }

    private Matrix getSelectedLsbChannel() {
        String sel = wmLsbChannel.getSelectionModel().getSelectedItem();
        return switch (sel) {
            case "Cb" -> process.getWorkingYCbCr().getCb();
            case "Cr" -> process.getWorkingYCbCr().getCr();
            default -> process.getWorkingYCbCr().getY();
        };
    }

    public void lsbEmbed() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load a watermark first.").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        int h = wmLsbBitPlane.getValue();
        int key = Integer.parseInt(wmLsbKey.getText());
        int strength = wmLsbStrength.getValue();
        boolean multi = wmMultiInsert.isSelected();
        Matrix channel = getSelectedLsbChannel();
        LsbWatermark.embed(channel, watermarkImage, h, key, strength, multi);
        lsbEmbedded = true;
        updateWatermarkControls();
        new Alert(AlertType.INFORMATION, "LSB watermark embedded into " + wmLsbChannel.getValue() + " at bit plane " + h + ".").showAndWait();
    }

    public void lsbExtract() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load the original watermark first (for dimensions).").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        int h = wmLsbBitPlane.getValue();
        int key = Integer.parseInt(wmLsbKey.getText());
        boolean multi = wmMultiInsert.isSelected();
        Matrix channel = getSelectedLsbChannel();
        extractedWatermark = LsbWatermark.extract(channel, watermarkImage.getWidth(), watermarkImage.getHeight(), h, key, multi);
        updateWatermarkControls();
        Dialogs.showImageInWindow(extractedWatermark, "Extracted LSB Watermark");
    }

    public void dctEmbed() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load a watermark first.").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        int block = wmDctBlock.getValue();
        int u1 = wmDctU1.getValue(), v1 = wmDctV1.getValue();
        int u2 = wmDctU2.getValue(), v2 = wmDctV2.getValue();
        double depth = Double.parseDouble(wmDctDepth.getText());
        boolean multi = wmMultiInsert.isSelected();
        Matrix yChannel = process.getWorkingYCbCr().getY();
        DctWatermark.embed(yChannel, watermarkImage, block, u1, v1, u2, v2, depth, multi);
        dctEmbedded = true;
        updateWatermarkControls();
        new Alert(AlertType.INFORMATION, "DCT watermark embedded into Y channel.").showAndWait();
    }

    public void dctExtract() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load original watermark first (for dimensions).").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        int block = wmDctBlock.getValue();
        int u1 = wmDctU1.getValue(), v1 = wmDctV1.getValue();
        int u2 = wmDctU2.getValue(), v2 = wmDctV2.getValue();
        boolean multi = wmMultiInsert.isSelected();
        Matrix yChannel = process.getWorkingYCbCr().getY();
        extractedWatermark = DctWatermark.extract(yChannel, watermarkImage.getWidth(), watermarkImage.getHeight(), block, u1, v1, u2, v2, multi);
        updateWatermarkControls();
        Dialogs.showImageInWindow(extractedWatermark, "Extracted DCT Watermark");
    }

    // ===== Spread Spectrum Handlers =====

    public void ssEmbed() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load a watermark first.").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        double alpha = Double.parseDouble(wmSsAlpha.getText());
        int key = Integer.parseInt(wmSsKey.getText());
        boolean multi = wmMultiInsert.isSelected();
        Matrix yChannel = process.getWorkingYCbCr().getY();
        SpreadSpectrumWatermark.embed(yChannel, watermarkImage, alpha, key, multi);
        ssEmbedded = true;
        updateWatermarkControls();
        new Alert(AlertType.INFORMATION, "Spread Spectrum watermark embedded into Y channel (alpha=" + alpha + ").").showAndWait();
    }

    public void ssExtract() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load original watermark first (for dimensions).").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        double alpha = Double.parseDouble(wmSsAlpha.getText());
        int key = Integer.parseInt(wmSsKey.getText());
        boolean multi = wmMultiInsert.isSelected();
        Matrix yChannel = process.getWorkingYCbCr().getY();
        extractedWatermark = SpreadSpectrumWatermark.extract(yChannel, watermarkImage.getWidth(), watermarkImage.getHeight(), alpha, key, multi);
        updateWatermarkControls();
        Dialogs.showImageInWindow(extractedWatermark, "Extracted Spread Spectrum Watermark");
    }

    // ===== Patchwork Handlers =====

    public void pwEmbed() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load a watermark first.").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        double delta = Double.parseDouble(wmPwDelta.getText());
        int key = Integer.parseInt(wmPwKey.getText());
        boolean multi = wmMultiInsert.isSelected();
        Matrix yChannel = process.getWorkingYCbCr().getY();
        PatchworkWatermark.embed(yChannel, watermarkImage, delta, key, multi);
        pwEmbedded = true;
        updateWatermarkControls();
        new Alert(AlertType.INFORMATION, "Patchwork watermark embedded into Y channel (delta=" + delta + ").").showAndWait();
    }

    public void pwExtract() {
        if (watermarkImage == null) { new Alert(AlertType.WARNING, "Load original watermark first (for dimensions).").showAndWait(); return; }
        if (!ycbcrActive) { new Alert(AlertType.WARNING, "Convert to YCbCr first.").showAndWait(); return; }
        double delta = Double.parseDouble(wmPwDelta.getText());
        int key = Integer.parseInt(wmPwKey.getText());
        boolean multi = wmMultiInsert.isSelected();
        Matrix yChannel = process.getWorkingYCbCr().getY();
        extractedWatermark = PatchworkWatermark.extract(yChannel, watermarkImage.getWidth(), watermarkImage.getHeight(), delta, key, multi);
        updateWatermarkControls();
        Dialogs.showImageInWindow(extractedWatermark, "Extracted Patchwork Watermark");
    }

    // ===== Attack Handlers =====
    // Attacks operate on the current watermarked image (RGB reconstruction).
    // They replace the working RGB and show both attacked image and extraction attempt.

    private BufferedImage getCurrentRgbImage() {
        return process.getImageFromRGB();
    }

    private void applyAttack(BufferedImage attacked, String name) {
        Dialogs.showImageInWindow(attacked, "After " + name);
        process.loadImage(attacked);
        lsbEmbedded = false;
        dctEmbedded = false;
        ssEmbedded = false;
        pwEmbedded = false;
        resetWorkflow();
    }

    public void attackJpeg() {
        float q = (float) attackJpegQuality.getValue();
        applyAttack(WatermarkAttacks.jpegCompress(getCurrentRgbImage(), q), "JPEG Compress (q=" + q + ")");
    }

    public void attackPng() {
        applyAttack(WatermarkAttacks.pngCompress(getCurrentRgbImage()), "PNG Compress");
    }

    public void attackRotate45() {
        applyAttack(WatermarkAttacks.rotate(getCurrentRgbImage(), 45), "Rotate 45°");
    }

    public void attackRotate90() {
        applyAttack(WatermarkAttacks.rotate(getCurrentRgbImage(), 90), "Rotate 90°");
    }

    public void attackResize75() {
        applyAttack(WatermarkAttacks.resize(getCurrentRgbImage(), 0.75), "Resize 75%");
    }

    public void attackResize50() {
        applyAttack(WatermarkAttacks.resize(getCurrentRgbImage(), 0.50), "Resize 50%");
    }

    public void attackMirror() {
        applyAttack(WatermarkAttacks.mirror(getCurrentRgbImage()), "Mirror");
    }

    public void attackCrop() {
        applyAttack(WatermarkAttacks.crop(getCurrentRgbImage(), 0.10), "Crop 10%");
    }

    // ===== Source Link Handlers =====

    public void openSsSource() {
        try { java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://ieeexplore.ieee.org/document/650120")); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public void openPwSource() {
        try { java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://ieeexplore.ieee.org/document/5387338")); }
        catch (Exception e) { e.printStackTrace(); }
    }
}