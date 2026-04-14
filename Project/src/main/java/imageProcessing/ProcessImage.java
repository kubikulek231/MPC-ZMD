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

    // load a new image and re-extract channels
    public void loadImage(String path) {
        this.originalImage = Dialogs.loadImageFromPath(path);
        if (originalImage != null) {
            this.imageHeight = originalImage.getHeight();
            this.imageWidth = originalImage.getWidth();
            extractRGBMatrices();
        }
    }

    // load an already-in-memory image (e.g. after attacks)
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

    // zero everything out so the pipeline has to be re-run
    public void resetModified() {
        workingState.reset(imageWidth, imageHeight);
    }

    // RGB -> YCbCr (BT.601)
    // keeps a snapshot in originalState so reset() can restore later
    // Matrix convention: Matrix(height, width), so column-subsampling = horizontal axis
    public void convertToYCbCr() {
        ImageColorSpaceConverter.convertRgbToYCbCr(originalState, workingState, imageWidth, imageHeight);
    }

    // YCbCr -> RGB (inverse of above)
    // Cb/Cr get upsampled if they were downsampled
    public void convertToRGB() {
        ImageColorSpaceConverter.convertYCbCrToRgb(workingState, imageWidth, imageHeight, lastSamplingType);
    }

    // subsample chroma (4:4:4 / 4:2:2 / 4:2:0 / 4:1:1) -- Y stays untouched
    public void applySampling(SamplingType type) {
        lastSamplingType = type;
        workingState.getYCbCr().setCb(Sampling.sampleDown(workingState.getYCbCr().getCb(), type));
        workingState.getYCbCr().setCr(Sampling.sampleDown(workingState.getYCbCr().getCr(), type));
    }

    // upsample Cb/Cr back to full size
    public void applyInverseSampling(SamplingType type) {
        workingState.getYCbCr().setCb(Sampling.sampleUp(workingState.getYCbCr().getCb(), type));
        workingState.getYCbCr().setCr(Sampling.sampleUp(workingState.getYCbCr().getCr(), type));
        // mark as fully upsampled so convertToRGB won't do it again
        lastSamplingType = SamplingType.S_4_4_4;
    }

    // run DCT or WHT on each NxN block of every channel
    public void applyTransform(TransformType type, int blockSize) {
        workingState.getYCbCr().setY(applyBlockTransform(workingState.getYCbCr().getY(), type, blockSize, false));
        workingState.getYCbCr().setCb(applyBlockTransform(workingState.getYCbCr().getCb(), type, blockSize, false));
        workingState.getYCbCr().setCr(applyBlockTransform(workingState.getYCbCr().getCr(), type, blockSize, false));
    }

    // inverse transform -- same block size and type as forward
    public void applyInverseTransform(TransformType type, int blockSize) {
        workingState.getYCbCr().setY(applyBlockTransform(workingState.getYCbCr().getY(), type, blockSize, true));
        workingState.getYCbCr().setCb(applyBlockTransform(workingState.getYCbCr().getCb(), type, blockSize, true));
        workingState.getYCbCr().setCr(applyBlockTransform(workingState.getYCbCr().getCr(), type, blockSize, true));
    }

    // divide transform coefficients by quant table steps
    // higher quality = smaller steps = less thrown away
    public void applyQuantization(int quality, int blockSize) {
        workingState.getYCbCr().setY(Quantization.quantize(workingState.getYCbCr().getY(), blockSize, quality, true));
        workingState.getYCbCr().setCb(Quantization.quantize(workingState.getYCbCr().getCb(), blockSize, quality, false));
        workingState.getYCbCr().setCr(Quantization.quantize(workingState.getYCbCr().getCr(), blockSize, quality, false));
    }

    // multiply back by quant steps -- can't recover the lost bits but restores the scale
    public void applyInverseQuantization(int quality, int blockSize) {
        workingState.getYCbCr().setY(Quantization.inverseQuantize(workingState.getYCbCr().getY(), blockSize, quality, true));
        workingState.getYCbCr().setCb(Quantization.inverseQuantize(workingState.getYCbCr().getCb(), blockSize, quality, false));
        workingState.getYCbCr().setCr(Quantization.inverseQuantize(workingState.getYCbCr().getCr(), blockSize, quality, false));
    }

    // ===== Extended quality methods using the Quality class =====

    /**
     * Compute MSE, MAE, SAE, PSNR for a channel.
     * Returns double[]{mse, mae, sae, psnr}.
     * For RGB/YCbCr types the values are averaged across 3 channels.
     */
    public double[] calculateMetrics(QualityType type) {
        return ImageMetricsCalculator.calculateMetrics(originalState, workingState, type);
    }

    /** SSIM and MSSIM for a YCbCr channel. Returns double[]{ssim, mssim}. */
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

    // MSE = avg squared diff per channel per pixel -- lower = less distortion
    public double calculateMSE() {
        return ImageMetricsCalculator.calculateRgbMse(originalState, workingState, imageWidth, imageHeight);
    }

    // PSNR in dB -- log-scale MSE, higher is better, inf = no loss
    public double calculatePSNR() {
        return ImageMetricsCalculator.calculateRgbPsnr(originalState, workingState, imageWidth, imageHeight);
    }

    // pack R/G/B arrays back into a BufferedImage
    public BufferedImage getImageFromRGB() {
        return ImagePreviewRenderer.renderRgbImage(workingState.getRgb(), imageWidth, imageHeight);
    }

    public enum ColorType { RED, GREEN, BLUE }

    // show one R/G/B channel -- tinted or greyscale depending on checkbox
    public BufferedImage showOneColorImageFromRGB(int[][] color, ColorType type, boolean greyScale) {
        return ImagePreviewRenderer.renderRgbChannel(color, type, greyScale, imageWidth, imageHeight);
    }

    // render a YCbCr channel as greyscale -- clamp to 0-255
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