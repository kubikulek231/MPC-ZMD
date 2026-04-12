package imageProcessing;

import java.awt.Color;
import java.awt.image.BufferedImage;

import Jama.Matrix;
import enums.QualityType;
import enums.SamplingType;
import enums.TransformType;
import graphics.Dialogs;
import jpeg.Quantization;
import jpeg.Sampling;
import jpeg.Transform;

public class ProcessImage {

    // ===== Fields =====

    public BufferedImage originalImage;
    public int imageHeight, imageWidth;

    private ImageState originalState;
    private ImageState workingState;

    // Remember which sampling was applied so convertToRGB can reverse it automatically
    private SamplingType lastSamplingType = SamplingType.S_4_4_4;

    public ProcessImage(BufferedImage image) {
        this.originalImage = image;
        if (image != null) {
            this.imageHeight = image.getHeight();
            this.imageWidth = image.getWidth();
            extractRGBMatrices();
        }
    }

    // load a new image from disk and re-extract all channel matrices
    public void loadImage(String path) {
        this.originalImage = Dialogs.loadImageFromPath(path);
        if (originalImage != null) {
            this.imageHeight = originalImage.getHeight();
            this.imageWidth = originalImage.getWidth();
            extractRGBMatrices();
        }
    }

    // load an already-in-memory image (used after attacks)
    public void loadImage(BufferedImage image) {
        this.originalImage = image;
        if (originalImage != null) {
            this.imageHeight = originalImage.getHeight();
            this.imageWidth = originalImage.getWidth();
            extractRGBMatrices();
        }
    }

    private void extractRGBMatrices() {
        originalState = new ImageState(imageWidth, imageHeight);
        workingState = new ImageState(imageWidth, imageHeight);

        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                Color color = new Color(originalImage.getRGB(x, y));
                originalState.getRgb().getRed()[x][y] = color.getRed();
                originalState.getRgb().getGreen()[x][y] = color.getGreen();
                originalState.getRgb().getBlue()[x][y] = color.getBlue();
            }
        }
    }

    // put all channels back to a black / zero state so the pipeline must be re-run from scratch
    public void resetModified() {
        workingState.reset(imageWidth, imageHeight);
    }

    // convert RGB -> YCbCr using SDTV (BT.601) coefficients
    // we keep originalY/Cb/Cr as a snapshot so reset() can restore them later
    // matrices use standard convention: Matrix(height, width) so cols = width = horizontal direction
    // that way column-subsampling in Sampling correctly halves the horizontal (width) axis
    public void convertToYCbCr() {
        ImageColorSpaceConverter.convertRgbToYCbCr(originalState, workingState, imageWidth, imageHeight);
    }

    // convert YCbCr back to RGB - inverse of the above
    // shift Cb/Cr by -128 first because they were stored with +128 offset
    // if Cb/Cr were downsampled, upsample into local temps - modifiedCb/Cr keep their downsampled size for display
    public void convertToRGB() {
        ImageColorSpaceConverter.convertYCbCrToRgb(workingState, imageWidth, imageHeight, lastSamplingType);
    }

    // throw away some chroma data based on the selected sampling mode (4:4:4 / 4:2:2 / 4:2:0 / 4:1:1)
    // Y is left alone - we only subsample Cb and Cr
    public void applySampling(SamplingType type) {
        lastSamplingType = type;
        workingState.getYCbCr().setCb(Sampling.sampleDown(workingState.getYCbCr().getCb(), type));
        workingState.getYCbCr().setCr(Sampling.sampleDown(workingState.getYCbCr().getCr(), type));
    }

    // upsample Cb and Cr back to full size by duplicating columns/rows
    public void applyInverseSampling(SamplingType type) {
        workingState.getYCbCr().setCb(Sampling.sampleUp(workingState.getYCbCr().getCb(), type));
        workingState.getYCbCr().setCr(Sampling.sampleUp(workingState.getYCbCr().getCr(), type));
        // mark as fully upsampled so convertToRGB won't do it again
        lastSamplingType = SamplingType.S_4_4_4;
    }

    // split each channel into NxN blocks and run DCT or WHT on each one
    public void applyTransform(TransformType type, int blockSize) {
        workingState.getYCbCr().setY(applyBlockTransform(workingState.getYCbCr().getY(), type, blockSize, false));
        workingState.getYCbCr().setCb(applyBlockTransform(workingState.getYCbCr().getCb(), type, blockSize, false));
        workingState.getYCbCr().setCr(applyBlockTransform(workingState.getYCbCr().getCr(), type, blockSize, false));
    }

    // inverse transform - same block size as forward, same type
    public void applyInverseTransform(TransformType type, int blockSize) {
        workingState.getYCbCr().setY(applyBlockTransform(workingState.getYCbCr().getY(), type, blockSize, true));
        workingState.getYCbCr().setCb(applyBlockTransform(workingState.getYCbCr().getCb(), type, blockSize, true));
        workingState.getYCbCr().setCr(applyBlockTransform(workingState.getYCbCr().getCr(), type, blockSize, true));
    }

    // divide each transform coefficient by a step value from the quantization table
    // higher quality = smaller steps = less data thrown away
    public void applyQuantization(int quality, int blockSize) {
        workingState.getYCbCr().setY(Quantization.quantize(workingState.getYCbCr().getY(), blockSize, quality, true));
        workingState.getYCbCr().setCb(Quantization.quantize(workingState.getYCbCr().getCb(), blockSize, quality, false));
        workingState.getYCbCr().setCr(Quantization.quantize(workingState.getYCbCr().getCr(), blockSize, quality, false));
    }

    // multiply back by the same step values - we can't recover what was lost, but the scale is restored
    public void applyInverseQuantization(int quality, int blockSize) {
        workingState.getYCbCr().setY(Quantization.inverseQuantize(workingState.getYCbCr().getY(), blockSize, quality, true));
        workingState.getYCbCr().setCb(Quantization.inverseQuantize(workingState.getYCbCr().getCb(), blockSize, quality, false));
        workingState.getYCbCr().setCr(Quantization.inverseQuantize(workingState.getYCbCr().getCr(), blockSize, quality, false));
    }

    // ===== Extended quality methods using the Quality class =====

    /**
     * Computes MSE, MAE, SAE and PSNR for the requested channel.
     * Returns double[]{mse, mae, sae, psnr}.
     * For RGB / YCbCr types the metric values are arithmetically averaged across the 3 channels.
     * Throws IllegalStateException if the required channel data is not yet available
     * (e.g.  Cb/Cr dimensions mismatch because subsampling was not reversed).
     */
    public double[] calculateMetrics(QualityType type) {
        return ImageMetricsCalculator.calculateMetrics(originalState, workingState, type);
    }

    /** Computes SSIM and MSSIM for the requested YCbCr channel. Returns double[]{ssim, mssim}. */
    public double[] calculateSSIMMetrics(QualityType type) {
        return ImageMetricsCalculator.calculateSSIMMetrics(originalState, workingState, type);
    }

    public ImageState getOriginalState() {
        return originalState;
    }

    public ImageState getWorkingState() {
        return workingState;
    }

    public ImageState.RgbChannels getOriginalRgb() {
        return originalState.getRgb();
    }

    public ImageState.RgbChannels getWorkingRgb() {
        return workingState.getRgb();
    }

    public ImageState.YCbCrChannels getOriginalYCbCr() {
        return originalState.getYCbCr();
    }

    public ImageState.YCbCrChannels getWorkingYCbCr() {
        return workingState.getYCbCr();
    }

    // MSE = average squared difference per channel per pixel - lower means less distortion
    public double calculateMSE() {
        return ImageMetricsCalculator.calculateRgbMse(originalState, workingState, imageWidth, imageHeight);
    }

    // PSNR in dB - log scale version of MSE, higher is better, infinity means no loss at all
    public double calculatePSNR() {
        return ImageMetricsCalculator.calculateRgbPsnr(originalState, workingState, imageWidth, imageHeight);
    }

    // pack the three separate R/G/B arrays back into a single BufferedImage
    public BufferedImage getImageFromRGB() {
        return ImagePreviewRenderer.renderRgbImage(workingState.getRgb(), imageWidth, imageHeight);
    }

    public enum ColorType { RED, GREEN, BLUE }

    // render one R/G/B channel - either tinted in its own color or as greyscale depending on the checkbox
    public BufferedImage showOneColorImageFromRGB(int[][] color, ColorType type, boolean greyScale) {
        return ImagePreviewRenderer.renderRgbChannel(color, type, greyScale, imageWidth, imageHeight);
    }

    // render a YCbCr channel matrix as greyscale - clamp values to 0-255 to avoid overflow
    // matrix is Matrix(height, width): rows=height, cols=width
    // matrix dimensions are used directly so it also works after downsampling
    public BufferedImage showOneColorImageFromYCbCr(Matrix color) {
        return ImagePreviewRenderer.renderYCbCrChannel(color);
    }

    // split matrix into NxN tiles, transform each tile, write results back
    // tiles that don't fit at the edge are left untouched
    private Matrix applyBlockTransform(Matrix m, TransformType type, int N, boolean inverse) {
        return inverse ? Transform.inverseTransform(m, type, N) : Transform.transform(m, type, N);
    }
}