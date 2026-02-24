package imageProcessing;

import java.awt.Color;
import java.awt.image.BufferedImage;

import Jama.Matrix;
import enums.SamplingType;
import enums.TransformType;
import graphics.Dialogs;

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

    // Standard JPEG 8×8 luminance quantization table
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

    // Standard JPEG 8×8 chrominance quantization table
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

    // ===== Constructors =====

    public ProcessImage(BufferedImage image) {
        this.originalImage = image;
        if (image != null) {
            this.imageHeight = image.getHeight();
            this.imageWidth = image.getWidth();
            extractRGBMatrices();
        }
    }

    // ===== Image Loading =====

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

        modifiedRed   = copyMatrix(originalRed);
        modifiedGreen = copyMatrix(originalGreen);
        modifiedBlue  = copyMatrix(originalBlue);
    }

    /** Deep-copy a 2-D int array. */
    private int[][] copyMatrix(int[][] source) {
        int[][] copy = new int[source.length][source[0].length];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, source[i].length);
        }
        return copy;
    }

    // ===== Reset =====

    /** Reset all modified channels back to their original state. */
    public void resetModified() {
        modifiedRed   = copyMatrix(originalRed);
        modifiedGreen = copyMatrix(originalGreen);
        modifiedBlue  = copyMatrix(originalBlue);

        if (originalY != null) {
            modifiedY  = originalY.copy();
            modifiedCb = originalCb.copy();
            modifiedCr = originalCr.copy();
        }
    }

    // ===== Color Space Conversion =====

    /**
     * Convert the modified RGB channels to YCbCr using BT.601.
     * Results are stored in modifiedY, modifiedCb, modifiedCr.
     * Original copies are preserved in originalY, originalCb, originalCr.
     */
    public void convertToYCbCr() {
        originalY  = new Matrix(imageWidth, imageHeight);
        originalCb = new Matrix(imageWidth, imageHeight);
        originalCr = new Matrix(imageWidth, imageHeight);

        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                double r = modifiedRed[x][y];
                double g = modifiedGreen[x][y];
                double b = modifiedBlue[x][y];

                originalY.set(x, y,   0.299  * r + 0.587  * g + 0.114  * b);
                originalCb.set(x, y, -0.169  * r - 0.331  * g + 0.500  * b + 128.0);
                originalCr.set(x, y,  0.500  * r - 0.419  * g - 0.081  * b + 128.0);
            }
        }

        modifiedY  = originalY.copy();
        modifiedCb = originalCb.copy();
        modifiedCr = originalCr.copy();
    }

    /**
     * Convert the modified YCbCr channels back to RGB (BT.601).
     * Results are written into modifiedRed, modifiedGreen, modifiedBlue.
     */
    public void convertToRGB() {
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                double Y  = modifiedY.get(x, y);
                double Cb = modifiedCb.get(x, y) - 128.0;
                double Cr = modifiedCr.get(x, y) - 128.0;

                modifiedRed[x][y]   = clamp((int) Math.round(Y + 1.402  * Cr));
                modifiedGreen[x][y] = clamp((int) Math.round(Y - 0.344  * Cb - 0.714 * Cr));
                modifiedBlue[x][y]  = clamp((int) Math.round(Y + 1.772  * Cb));
            }
        }
    }

    // ===== Chroma Subsampling =====

    /**
     * Apply chroma subsampling to modifiedCb and modifiedCr.
     * The matrices stay the same size – subsampled blocks are filled with their average.
     */
    public void applySampling(SamplingType type) {
        int hFactor = 1, vFactor = 1;
        switch (type) {
            case S_4_2_2 -> hFactor = 2;
            case S_4_2_0 -> { hFactor = 2; vFactor = 2; }
            case S_4_1_1 -> hFactor = 4;
            default -> { return; } // 4:4:4 – nothing to do
        }
        modifiedCb = subsampleMatrix(modifiedCb, hFactor, vFactor);
        modifiedCr = subsampleMatrix(modifiedCr, hFactor, vFactor);
    }

    /**
     * Inverse chroma subsampling – bilinear interpolation within each block.
     * (For 4:4:4 this is a no-op; for other modes the block-average is already
     * at full resolution, so smooth it with bilinear interpolation.)
     */
    public void applyInverseSampling(SamplingType type) {
        int hFactor = 1, vFactor = 1;
        switch (type) {
            case S_4_2_2 -> hFactor = 2;
            case S_4_2_0 -> { hFactor = 2; vFactor = 2; }
            case S_4_1_1 -> hFactor = 4;
            default -> { return; }
        }
        modifiedCb = bilinearUpsample(modifiedCb, hFactor, vFactor);
        modifiedCr = bilinearUpsample(modifiedCr, hFactor, vFactor);
    }

    // ===== Block Transform =====

    /**
     * Apply the chosen block transform to the YCbCr channels.
     *
     * @param type      DCT or WHT
     * @param blockSize Size of the N×N transform block (must be a power of 2)
     */
    public void applyTransform(TransformType type, int blockSize) {
        modifiedY  = applyBlockTransform(modifiedY,  type, blockSize, false);
        modifiedCb = applyBlockTransform(modifiedCb, type, blockSize, false);
        modifiedCr = applyBlockTransform(modifiedCr, type, blockSize, false);
    }

    /**
     * Inverse block transform on the YCbCr channels.
     *
     * @param type      DCT or WHT
     * @param blockSize Size of the N×N transform block used during forward transform
     */
    public void applyInverseTransform(TransformType type, int blockSize) {
        modifiedY  = applyBlockTransform(modifiedY,  type, blockSize, true);
        modifiedCb = applyBlockTransform(modifiedCb, type, blockSize, true);
        modifiedCr = applyBlockTransform(modifiedCr, type, blockSize, true);
    }

    // ===== Quantization =====

    /**
     * Quantize the transform coefficients using a JPEG-style quantization table.
     * For 8×8 blocks the standard JPEG luma/chroma tables are used; for other sizes
     * a uniform step size derived from the quality factor is applied.
     *
     * @param quality   Quality factor 1–100 (higher = better quality, less compression)
     * @param blockSize Transform block size
     */
    public void applyQuantization(int quality, int blockSize) {
        double scale  = qualityToScale(quality);
        modifiedY  = quantizeMatrix(modifiedY,  scale, blockSize, false, false);
        modifiedCb = quantizeMatrix(modifiedCb, scale, blockSize, false, true);
        modifiedCr = quantizeMatrix(modifiedCr, scale, blockSize, false, true);
    }

    /**
     * Inverse quantization (dequantization) of the YCbCr channels.
     *
     * @param quality   Quality factor used during quantization
     * @param blockSize Transform block size used during quantization
     */
    public void applyInverseQuantization(int quality, int blockSize) {
        double scale  = qualityToScale(quality);
        modifiedY  = quantizeMatrix(modifiedY,  scale, blockSize, true, false);
        modifiedCb = quantizeMatrix(modifiedCb, scale, blockSize, true, true);
        modifiedCr = quantizeMatrix(modifiedCr, scale, blockSize, true, true);
    }

    // ===== Quality Metrics =====

    /**
     * Calculate Mean Squared Error between original and modified RGB channels.
     *
     * @return MSE value (lower is better)
     */
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

    /**
     * Calculate Peak Signal-to-Noise Ratio between original and modified RGB.
     *
     * @return PSNR in dB (higher is better; returns Double.POSITIVE_INFINITY for lossless)
     */
    public double calculatePSNR() {
        double mse = calculateMSE();
        if (mse == 0) return Double.POSITIVE_INFINITY;
        return 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    // ===== Image Rendering =====

    /** Build a BufferedImage from the modified RGB channels. */
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

    /**
     * Render a single RGB channel as a greyscale or tinted image.
     *
     * @param color     2-D channel data [x][y]
     * @param type      Which channel this contains
     * @param greyScale true → render as grey; false → render in its native colour
     */
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

    /**
     * Render a single YCbCr channel matrix as a greyscale image.
     *
     * @param color Matrix with channel values [x][y]
     */
    public BufferedImage showOneColorImageFromYCbCr(Matrix color) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int v = clamp((int) Math.round(color.get(x, y)));
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return image;
    }

    // ===== Private Helpers =====

    /** Clamp an integer to [0, 255]. */
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Average each hFactor×vFactor block in the matrix and fill it with that average
     * (simulates discarding intra-block detail).
     */
    private Matrix subsampleMatrix(Matrix m, int hFactor, int vFactor) {
        Matrix result = new Matrix(imageWidth, imageHeight);
        for (int bx = 0; bx < imageWidth; bx += hFactor) {
            for (int by = 0; by < imageHeight; by += vFactor) {
                double sum = 0;
                int count  = 0;
                for (int dx = 0; dx < hFactor && bx + dx < imageWidth; dx++) {
                    for (int dy = 0; dy < vFactor && by + dy < imageHeight; dy++) {
                        sum += m.get(bx + dx, by + dy);
                        count++;
                    }
                }
                double avg = sum / count;
                for (int dx = 0; dx < hFactor && bx + dx < imageWidth; dx++) {
                    for (int dy = 0; dy < vFactor && by + dy < imageHeight; dy++) {
                        result.set(bx + dx, by + dy, avg);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Bilinear interpolation within each hFactor×vFactor block to smooth
     * the block-constant values produced by subsampling.
     */
    private Matrix bilinearUpsample(Matrix m, int hFactor, int vFactor) {
        Matrix result = new Matrix(imageWidth, imageHeight);
        for (int bx = 0; bx < imageWidth; bx += hFactor) {
            for (int by = 0; by < imageHeight; by += vFactor) {
                // Gather corner values (or edge-clamped neighbours)
                double tl = m.get(bx, by);
                double tr = m.get(Math.min(bx + hFactor, imageWidth  - 1), by);
                double bl = m.get(bx, Math.min(by + vFactor, imageHeight - 1));
                double br = m.get(Math.min(bx + hFactor, imageWidth  - 1),
                                  Math.min(by + vFactor, imageHeight - 1));

                for (int dx = 0; dx < hFactor && bx + dx < imageWidth; dx++) {
                    for (int dy = 0; dy < vFactor && by + dy < imageHeight; dy++) {
                        double tx = (hFactor > 1) ? (double) dx / (hFactor - 1) : 0;
                        double ty = (vFactor > 1) ? (double) dy / (vFactor - 1) : 0;
                        double v  = tl * (1 - tx) * (1 - ty)
                                  + tr *      tx  * (1 - ty)
                                  + bl * (1 - tx) *      ty
                                  + br *      tx  *      ty;
                        result.set(bx + dx, by + dy, v);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Apply a 2-D block transform to every non-overlapping N×N block.
     * Pixels that fall outside a full block are copied unchanged.
     */
    private Matrix applyBlockTransform(Matrix m, TransformType type, int N, boolean inverse) {
        Matrix result = m.copy();
        for (int bx = 0; bx + N <= imageWidth; bx += N) {
            for (int by = 0; by + N <= imageHeight; by += N) {
                double[][] block = extractBlock(m, bx, by, N);
                double[][] transformed = (type == TransformType.DCT)
                        ? (inverse ? computeIDCT(block) : computeDCT(block))
                        : (inverse ? computeIWHT(block) : computeWHT(block));
                setBlock(result, transformed, bx, by);
            }
        }
        return result;
    }

    /** Extract an N×N block from matrix m starting at (bx, by). */
    private double[][] extractBlock(Matrix m, int bx, int by, int N) {
        double[][] block = new double[N][N];
        for (int dx = 0; dx < N; dx++)
            for (int dy = 0; dy < N; dy++)
                block[dx][dy] = m.get(bx + dx, by + dy);
        return block;
    }

    /** Write an N×N block back into the matrix starting at (bx, by). */
    private void setBlock(Matrix m, double[][] block, int bx, int by) {
        int N = block.length;
        for (int dx = 0; dx < N; dx++)
            for (int dy = 0; dy < N; dy++)
                m.set(bx + dx, by + dy, block[dx][dy]);
    }

    // --- DCT ---

    /** 2-D forward DCT of an N×N block (orthonormal form). */
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

    /** 2-D inverse DCT of an N×N coefficient block. */
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

    // --- WHT ---

    /** 2-D forward Walsh–Hadamard Transform (normalised). */
    private double[][] computeWHT(double[][] block) {
        int N = block.length;
        Matrix H = buildHadamard(N);
        Matrix f = new Matrix(block);
        // F = (1/N) * H * f * H
        return H.times(f).times(H).times(1.0 / N).getArray();
    }

    /**
     * 2-D inverse WHT – identical to the forward WHT because the normalised
     * Hadamard matrix is its own inverse scaled by 1/N.
     */
    private double[][] computeIWHT(double[][] block) {
        return computeWHT(block);
    }

    /**
     * Recursively build an unnormalised N×N Hadamard matrix.
     * N must be a power of 2.
     */
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

    // --- Quantization helpers ---

    /** Convert quality factor (1–100) to a scaling factor for the quantization table. */
    private static double qualityToScale(int quality) {
        if (quality <= 0)   quality = 1;
        if (quality > 100)  quality = 100;
        if (quality < 50)   return 50.0 / quality;
        return (100.0 - quality) / 50.0;
    }

    /**
     * Return the quantization step for position (u, v) in an N×N block.
     * For 8×8 blocks the standard JPEG table is used; for other sizes a
     * uniform step size is derived from the quality scale.
     */
    private static double quantStep(int u, int v, int N, double scale, boolean chroma) {
        if (N == 8) {
            int base = chroma ? JPEG_CHROMA_TABLE[u][v] : JPEG_LUMA_TABLE[u][v];
            return Math.max(1, Math.round(base * scale));
        }
        // Fallback: uniform quantization – step grows linearly with frequency
        double baseStep = 1 + (u + v);
        return Math.max(1, Math.round(baseStep * (scale + 0.5)));
    }

    /**
     * Quantize (forward) or dequantize (inverse) every N×N block of a matrix.
     *
     * @param m        Input matrix
     * @param scale    Quality scale factor from {@link #qualityToScale(int)}
     * @param N        Block size
     * @param inverse  true for dequantization, false for quantization
     * @param chroma   true for chrominance table, false for luminance
     */
    private Matrix quantizeMatrix(Matrix m, double scale, int N, boolean inverse, boolean chroma) {
        Matrix result = m.copy();
        for (int bx = 0; bx + N <= imageWidth; bx += N) {
            for (int by = 0; by + N <= imageHeight; by += N) {
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