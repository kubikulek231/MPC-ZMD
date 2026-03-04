package imageProcessing;

import java.awt.Color;
import java.awt.image.BufferedImage;

import Jama.Matrix;
import enums.QualityType;
import enums.SamplingType;
import enums.TransformType;
import graphics.Dialogs;
import jpeg.Quality;
import jpeg.Sampling;

public class ProcessImage {

    // ===== Fields =====

    public BufferedImage originalImage;
    public int imageHeight, imageWidth;

    public int[][] originalRed, modifiedRed;
    public int[][] originalGreen, modifiedGreen;
    public int[][] originalBlue, modifiedBlue;

    public Matrix originalY, modifiedY;
    public Matrix originalCb, modifiedCb;
    public Matrix originalCr, modifiedCr;

    // remember which sampling was applied so convertToRGB can reverse it automatically
    private SamplingType lastSamplingType = SamplingType.S_4_4_4;

    // standard 8x8 quantization table for luma (Y) - JPEG spec values
    private static final int[][] JPEG_LUMA_TABLE = {
        {16, 11, 10, 16, 24, 40, 51, 61},
        {12, 12, 14, 19, 26, 58, 60, 55},
        {14, 13, 16, 24, 40, 57, 69, 56},
        {14, 17, 22, 29, 51, 87, 80, 62},
        {18, 22, 37, 56, 68, 109, 103, 77},
        {24, 35, 55, 64, 81, 104, 113, 92},
        {49, 64, 78, 87, 103, 121, 120, 101},
        {72, 92, 95, 98, 112, 100, 103, 99}
    };

    // same for chroma (Cb, Cr) - these are generally higher values because we can compress chroma more aggressively
    private static final int[][] JPEG_CHROMA_TABLE = {
        {17, 18, 24, 47, 99, 99, 99, 99},
        {18, 21, 26, 66, 99, 99, 99, 99},
        {24, 26, 56, 99, 99, 99, 99, 99},
        {47, 66, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99}
    };

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

    private void extractRGBMatrices() {
        originalRed   = new int[imageWidth][imageHeight];
        originalGreen = new int[imageWidth][imageHeight];
        originalBlue  = new int[imageWidth][imageHeight];

        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                Color color = new Color(originalImage.getRGB(x, y));
                originalRed[x][y]   = color.getRed();
                originalGreen[x][y] = color.getGreen();
                originalBlue[x][y]  = color.getBlue();
            }
        }

        // modified RGB starts as black — populated only after a full YCbCr → RGB round-trip
        modifiedRed   = new int[imageWidth][imageHeight];
        modifiedGreen = new int[imageWidth][imageHeight];
        modifiedBlue  = new int[imageWidth][imageHeight];

        // YCbCr matrices start as zero (black) so viewers work immediately without crashing
        originalY  = new Matrix(imageHeight, imageWidth);
        originalCb = new Matrix(imageHeight, imageWidth);
        originalCr = new Matrix(imageHeight, imageWidth);
        modifiedY  = new Matrix(imageHeight, imageWidth);
        modifiedCb = new Matrix(imageHeight, imageWidth);
        modifiedCr = new Matrix(imageHeight, imageWidth);
    }

    // just a helper to deep-copy a 2D int array - System.arraycopy is faster than a manual loop
    private int[][] copyMatrix(int[][] source) {
        int[][] copy = new int[source.length][source[0].length];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, source[i].length);
        }
        return copy;
    }

    // put all channels back to a black / zero state so the pipeline must be re-run from scratch
    public void resetModified() {
        modifiedRed   = new int[imageWidth][imageHeight];
        modifiedGreen = new int[imageWidth][imageHeight];
        modifiedBlue  = new int[imageWidth][imageHeight];

        originalY  = new Matrix(imageHeight, imageWidth);
        originalCb = new Matrix(imageHeight, imageWidth);
        originalCr = new Matrix(imageHeight, imageWidth);
        modifiedY  = new Matrix(imageHeight, imageWidth);
        modifiedCb = new Matrix(imageHeight, imageWidth);
        modifiedCr = new Matrix(imageHeight, imageWidth);
    }

    // convert RGB -> YCbCr using SDTV (BT.601) coefficients
    // we keep originalY/Cb/Cr as a snapshot so reset() can restore them later
    // matrices use standard convention: Matrix(height, width) so cols = width = horizontal direction
    // that way column-subsampling in Sampling correctly halves the horizontal (width) axis
    public void convertToYCbCr() {
        originalY  = new Matrix(imageHeight, imageWidth);
        originalCb = new Matrix(imageHeight, imageWidth);
        originalCr = new Matrix(imageHeight, imageWidth);

        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                double r = originalRed[x][y];
                double g = originalGreen[x][y];
                double b = originalBlue[x][y];

                originalY.set(y, x,   0.299  * r + 0.587  * g + 0.114  * b);
                originalCb.set(y, x, -0.169  * r - 0.331  * g + 0.500  * b + 128.0);
                originalCr.set(y, x,  0.500  * r - 0.419  * g - 0.081  * b + 128.0);
            }
        }

        modifiedY  = originalY.copy();
        modifiedCb = originalCb.copy();
        modifiedCr = originalCr.copy();
    }

    // convert YCbCr back to RGB - inverse of the above
    // shift Cb/Cr by -128 first because they were stored with +128 offset
    // if Cb/Cr were downsampled, upsample into local temps - modifiedCb/Cr keep their downsampled size for display
    public void convertToRGB() {
        Matrix cbFull = (modifiedCb.getRowDimension() != imageHeight || modifiedCb.getColumnDimension() != imageWidth)
                ? Sampling.sampleUp(modifiedCb, lastSamplingType)
                : modifiedCb;
        Matrix crFull = (modifiedCr.getRowDimension() != imageHeight || modifiedCr.getColumnDimension() != imageWidth)
                ? Sampling.sampleUp(modifiedCr, lastSamplingType)
                : modifiedCr;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                double Y  = modifiedY.get(y, x);
                double Cb = cbFull.get(y, x) - 128.0;
                double Cr = crFull.get(y, x) - 128.0;

                modifiedRed[x][y]   = clamp((int) Math.round(Y + 1.402  * Cr));
                modifiedGreen[x][y] = clamp((int) Math.round(Y - 0.344  * Cb - 0.714 * Cr));
                modifiedBlue[x][y]  = clamp((int) Math.round(Y + 1.772  * Cb));
            }
        }
    }

    // throw away some chroma data based on the selected sampling mode (4:4:4 / 4:2:2 / 4:2:0 / 4:1:1)
    // Y is left alone - we only subsample Cb and Cr
    public void applySampling(SamplingType type) {
        lastSamplingType = type;
        modifiedCb = Sampling.sampleDown(modifiedCb, type);
        modifiedCr = Sampling.sampleDown(modifiedCr, type);
    }

    // upsample Cb and Cr back to full size by duplicating columns/rows
    public void applyInverseSampling(SamplingType type) {
        modifiedCb = Sampling.sampleUp(modifiedCb, type);
        modifiedCr = Sampling.sampleUp(modifiedCr, type);
        // mark as fully upsampled so convertToRGB won't do it again
        lastSamplingType = SamplingType.S_4_4_4;
    }

    // split each channel into NxN blocks and run DCT or WHT on each one
    public void applyTransform(TransformType type, int blockSize) {
        modifiedY  = applyBlockTransform(modifiedY,  type, blockSize, false);
        modifiedCb = applyBlockTransform(modifiedCb, type, blockSize, false);
        modifiedCr = applyBlockTransform(modifiedCr, type, blockSize, false);
    }

    // inverse transform - same block size as forward, same type
    public void applyInverseTransform(TransformType type, int blockSize) {
        modifiedY  = applyBlockTransform(modifiedY,  type, blockSize, true);
        modifiedCb = applyBlockTransform(modifiedCb, type, blockSize, true);
        modifiedCr = applyBlockTransform(modifiedCr, type, blockSize, true);
    }

    // divide each transform coefficient by a step value from the quantization table
    // higher quality = smaller steps = less data thrown away
    public void applyQuantization(int quality, int blockSize) {
        double scale  = qualityToScale(quality);
        modifiedY  = quantizeMatrix(modifiedY,  scale, blockSize, false, false);
        modifiedCb = quantizeMatrix(modifiedCb, scale, blockSize, false, true);
        modifiedCr = quantizeMatrix(modifiedCr, scale, blockSize, false, true);
    }

    // multiply back by the same step values - we can't recover what was lost, but the scale is restored
    public void applyInverseQuantization(int quality, int blockSize) {
        double scale  = qualityToScale(quality);
        modifiedY  = quantizeMatrix(modifiedY,  scale, blockSize, true, false);
        modifiedCb = quantizeMatrix(modifiedCb, scale, blockSize, true, true);
        modifiedCr = quantizeMatrix(modifiedCr, scale, blockSize, true, true);
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
        switch (type) {
            case RED:   return metricsForChannels(Quality.convertIntToDouble(originalRed),   Quality.convertIntToDouble(modifiedRed));
            case GREEN: return metricsForChannels(Quality.convertIntToDouble(originalGreen), Quality.convertIntToDouble(modifiedGreen));
            case BLUE:  return metricsForChannels(Quality.convertIntToDouble(originalBlue),  Quality.convertIntToDouble(modifiedBlue));
            case Y:     return metricsForChannels(originalY.getArray(), modifiedY.getArray());
            case CB:    return metricsForMatchedChannels(originalCb, modifiedCb, "Cb");
            case CR:    return metricsForMatchedChannels(originalCr, modifiedCr, "Cr");
            case RGB: {
                double[] r = metricsForChannels(Quality.convertIntToDouble(originalRed),   Quality.convertIntToDouble(modifiedRed));
                double[] g = metricsForChannels(Quality.convertIntToDouble(originalGreen), Quality.convertIntToDouble(modifiedGreen));
                double[] b = metricsForChannels(Quality.convertIntToDouble(originalBlue),  Quality.convertIntToDouble(modifiedBlue));
                double mse  = (r[0] + g[0] + b[0]) / 3.0;
                double mae  = (r[1] + g[1] + b[1]) / 3.0;
                double sae  = (r[2] + g[2] + b[2]) / 3.0;
                return new double[]{mse, mae, sae, Quality.countPSNR(mse)};
            }
            case YCBCR: {
                double[] y  = metricsForChannels(originalY.getArray(),  modifiedY.getArray());
                double[] cb = metricsForMatchedChannels(originalCb, modifiedCb, "Cb");
                double[] cr = metricsForMatchedChannels(originalCr, modifiedCr, "Cr");
                double mse  = (y[0] + cb[0] + cr[0]) / 3.0;
                double mae  = (y[1] + cb[1] + cr[1]) / 3.0;
                double sae  = (y[2] + cb[2] + cr[2]) / 3.0;
                return new double[]{mse, mae, sae, Quality.countPSNR(mse)};
            }
            default: throw new IllegalArgumentException("Unknown QualityType: " + type);
        }
    }

    /** Computes SSIM and MSSIM for the requested YCbCr channel. Returns double[]{ssim, mssim}. */
    public double[] calculateSSIMMetrics(QualityType type) {
        switch (type) {
            case Y:  return ssimForChannels(originalY.getArray(),  modifiedY.getArray());
            case CB: return ssimForMatchedChannels(originalCb, modifiedCb, "Cb");
            case CR: return ssimForMatchedChannels(originalCr, modifiedCr, "Cr");
            default: throw new IllegalArgumentException("SSIM is only supported for Y, Cb and Cr channels.");
        }
    }

    private double[] metricsForChannels(double[][] orig, double[][] mod) {
        double mse = Quality.countMSE(orig, mod);
        return new double[]{mse, Quality.countMAE(orig, mod), Quality.countSAE(orig, mod), Quality.countPSNR(mse)};
    }

    private double[] metricsForMatchedChannels(Matrix orig, Matrix mod, String name) {
        if (orig.getRowDimension() != mod.getRowDimension()
                || orig.getColumnDimension() != mod.getColumnDimension()) {
            throw new IllegalStateException(
                    name + " channel dimensions don't match (" +
                    orig.getRowDimension() + "×" + orig.getColumnDimension() + " vs " +
                    mod.getRowDimension()  + "×" + mod.getColumnDimension()  +
                    "). Apply inverse subsampling first.");
        }
        return metricsForChannels(orig.getArray(), mod.getArray());
    }

    private double[] ssimForChannels(double[][] orig, double[][] mod) {
        return new double[]{Quality.countSSIM(new Matrix(orig), new Matrix(mod)),
                            Quality.countMSSIM(new Matrix(orig), new Matrix(mod))};
    }

    private double[] ssimForMatchedChannels(Matrix orig, Matrix mod, String name) {
        if (orig.getRowDimension() != mod.getRowDimension()
                || orig.getColumnDimension() != mod.getColumnDimension()) {
            throw new IllegalStateException(
                    name + " channel dimensions don't match. Apply inverse subsampling first.");
        }
        return ssimForChannels(orig.getArray(), mod.getArray());
    }

    // MSE = average squared difference per channel per pixel - lower means less distortion
    public double calculateMSE() {
        long sum = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int dr = originalRed[x][y]   - modifiedRed[x][y];
                int dg = originalGreen[x][y] - modifiedGreen[x][y];
                int db = originalBlue[x][y]  - modifiedBlue[x][y];
                sum += dr * dr + dg * dg + db * db;
            }
        }
        return (double) sum / (3L * imageWidth * imageHeight);
    }

    // PSNR in dB - log scale version of MSE, higher is better, infinity means no loss at all
    public double calculatePSNR() {
        double mse = calculateMSE();
        if (mse == 0) return Double.POSITIVE_INFINITY;
        return 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    // pack the three separate R/G/B arrays back into a single BufferedImage
    public BufferedImage getImageFromRGB() {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int rgb = (modifiedRed[x][y] << 16) | (modifiedGreen[x][y] << 8) | modifiedBlue[x][y];
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    public enum ColorType { RED, GREEN, BLUE }

    // render one R/G/B channel - either tinted in its own color or as greyscale depending on the checkbox
    public BufferedImage showOneColorImageFromRGB(int[][] color, ColorType type, boolean greyScale) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int value = color[x][y];
                int r = greyScale ? value : (type == ColorType.RED   ? value : 0);
                int g = greyScale ? value : (type == ColorType.GREEN ? value : 0);
                int b = greyScale ? value : (type == ColorType.BLUE  ? value : 0);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    // render a YCbCr channel matrix as greyscale - clamp values to 0-255 to avoid overflow
    // matrix is Matrix(height, width): rows=height, cols=width
    // matrix dimensions are used directly so it also works after downsampling
    public BufferedImage showOneColorImageFromYCbCr(Matrix color) {
        int width  = color.getColumnDimension();
        int height = color.getRowDimension();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int v = clamp((int) Math.round(color.get(y, x)));
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return image;
    }

    // keep pixel values in valid range [0, 255]
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // split matrix into NxN tiles, transform each tile, write results back
    // tiles that don't fit at the edge are left untouched
    private Matrix applyBlockTransform(Matrix m, TransformType type, int N, boolean inverse) {
        int width = m.getRowDimension();
        int height = m.getColumnDimension();
        Matrix result = m.copy();
        for (int bx = 0; bx + N <= width; bx += N) {
            for (int by = 0; by + N <= height; by += N) {
                double[][] block = extractBlock(m, bx, by, N);
                double[][] transformed = (type == TransformType.DCT)
                        ? (inverse ? computeIDCT(block) : computeDCT(block))
                        : (inverse ? computeIWHT(block) : computeWHT(block));
                setBlock(result, transformed, bx, by);
            }
        }
        return result;
    }

    // copy an NxN region out of the matrix into a plain 2D array for the transform
    private double[][] extractBlock(Matrix m, int bx, int by, int N) {
        double[][] block = new double[N][N];
        for (int dx = 0; dx < N; dx++)
            for (int dy = 0; dy < N; dy++)
                block[dx][dy] = m.get(bx + dx, by + dy);
        return block;
    }

    // write the transformed block back into the right position in the matrix
    private void setBlock(Matrix m, double[][] block, int bx, int by) {
        int N = block.length;
        for (int dx = 0; dx < N; dx++)
            for (int dy = 0; dy < N; dy++)
                m.set(bx + dx, by + dy, block[dx][dy]);
    }

    // 2D forward DCT - transforms spatial pixel values into frequency coefficients
    // cu/cv are normalization factors so the DC coefficient gets the 1/sqrt(2) weight
    private double[][] computeDCT(double[][] block) {
        int N = block.length;
        double[][] F = new double[N][N];
        double norm = 2.0 / N;
        for (int u = 0; u < N; u++) {
            double cu = (u == 0) ? 1.0 / Math.sqrt(2) : 1.0;
            for (int v = 0; v < N; v++) {
                double cv = (v == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                double sum = 0;
                for (int x = 0; x < N; x++)
                    for (int y = 0; y < N; y++)
                        sum += block[x][y]
                             * Math.cos((2 * x + 1) * u * Math.PI / (2.0 * N))
                             * Math.cos((2 * y + 1) * v * Math.PI / (2.0 * N));
                F[u][v] = norm * cu * cv * sum;
            }
        }
        return F;
    }

    // 2D inverse DCT - reconstructs pixel values from the frequency coefficients
    private double[][] computeIDCT(double[][] F) {
        int N = F.length;
        double[][] f = new double[N][N];
        double norm = 2.0 / N;
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                double sum = 0;
                for (int u = 0; u < N; u++) {
                    double cu = (u == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                    for (int v = 0; v < N; v++) {
                        double cv = (v == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                        sum += cu * cv * F[u][v]
                             * Math.cos((2 * x + 1) * u * Math.PI / (2.0 * N))
                             * Math.cos((2 * y + 1) * v * Math.PI / (2.0 * N));
                    }
                }
                f[x][y] = norm * sum;
            }
        }
        return f;
    }

    // 2D WHT using the Hadamard matrix: F = (1/N) * H * f * H
    private double[][] computeWHT(double[][] block) {
        int N = block.length;
        Matrix H = buildHadamard(N);
        Matrix f = new Matrix(block);
        return H.times(f).times(H).times(1.0 / N).getArray();
    }

    // inverse WHT is the same operation as forward WHT - the Hadamard matrix is self-inverse up to scaling
    private double[][] computeIWHT(double[][] block) {
        return computeWHT(block);
    }

    // build the Hadamard matrix recursively using the [H H; H -H] pattern, N must be power of 2
    private Matrix buildHadamard(int N) {
        if (N == 1) return new Matrix(new double[][]{{1}});
        Matrix h    = buildHadamard(N / 2);
        Matrix result = new Matrix(N, N);
        int half = N / 2;
        for (int i = 0; i < half; i++) {
            for (int j = 0; j < half; j++) {
                double v = h.get(i, j);
                result.set(i,        j,        v);
                result.set(i,        j + half,  v);
                result.set(i + half, j,         v);
                result.set(i + half, j + half, -v);
            }
        }
        return result;
    }

    // convert quality 1-100 to a multiplier for the quantization table
    // quality 50 = scale 1.0 (original JPEG tables), quality 1 = very aggressive compression
    private static double qualityToScale(int quality) {
        if (quality <= 0)   quality = 1;
        if (quality > 100)  quality = 100;
        if (quality < 50)   return 50.0 / quality;
        return (100.0 - quality) / 50.0;
    }

    // look up the quantization step for one coefficient position
    // for 8x8 blocks we use the standard JPEG tables, otherwise just scale by frequency index
    private static double quantStep(int u, int v, int N, double scale, boolean chroma) {
        if (N == 8) {
            int base = chroma ? JPEG_CHROMA_TABLE[u][v] : JPEG_LUMA_TABLE[u][v];
            return Math.max(1, Math.round(base * scale));
        }
        // for non-8x8 blocks: use a simple step that gets larger for higher frequencies
        double baseStep = 1 + (u + v);
        return Math.max(1, Math.round(baseStep * (scale + 0.5)));
    }

    // quantize or dequantize all NxN blocks in the matrix
    // forward: divide and round (loses precision), inverse: multiply back
    private Matrix quantizeMatrix(Matrix m, double scale, int N, boolean inverse, boolean chroma) {
        int width = m.getRowDimension();
        int height = m.getColumnDimension();
        Matrix result = m.copy();
        for (int bx = 0; bx + N <= width; bx += N) {
            for (int by = 0; by + N <= height; by += N) {
                for (int u = 0; u < N; u++) {
                    for (int v = 0; v < N; v++) {
                        double step  = quantStep(u, v, N, scale, chroma);
                        double value = m.get(bx + u, by + v);
                        result.set(bx + u, by + v,
                                inverse ? value * step : Math.round(value / step));
                    }
                }
            }
        }
        return result;
    }
}