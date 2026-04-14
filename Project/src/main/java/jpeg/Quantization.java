package jpeg;

import Jama.Matrix;

// JPEG-like quantization and dequantization on block-transformed data.
public class Quantization {

    /** Standard JPEG luminance (Y) quantization table of the JPEG spec .
     *  Lower values in the top-left preserve low-frequency detail; */
    public static final double[][] quantizationMatrix8Y = {
        {16, 11, 10, 16, 24, 40, 51, 61},
        {12, 12, 14, 19, 26, 58, 60, 55},
        {14, 13, 16, 24, 40, 57, 69, 56},
        {14, 17, 22, 29, 51, 87, 80, 62},
        {18, 22, 37, 56, 68, 109, 103, 77},
        {24, 35, 55, 64, 81, 104, 113, 92},
        {49, 64, 78, 87, 103, 121, 120, 101},
        {72, 92, 95, 98, 112, 100, 103, 99}
    };

    /** Standard JPEG chrominance (Cb/Cr) quantization table of the JPEG spec.
     *  Way more aggressive than the luma one -- eyes don't care as much about color detail */
    public static final double[][] quantizationMatrix8C = {
        // Lowest frequencies, coarser details preserved
        {17, 18, 24, 47, 99, 99, 99, 99},
        {18, 21, 26, 66, 99, 99, 99, 99},
        {24, 26, 56, 99, 99, 99, 99, 99},
        {47, 66, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99} // Highest frequencies, finer details filtered
    };

    public double[][] getQuantizationMatrix8Y() {
        return quantizationMatrix8Y;
    }

    public double[][] getQuantizationMatrix8C() {
        return quantizationMatrix8C;
    }

    public static Matrix getQuantizationMatrix(int blockSize, int quality, boolean matrixY) {
        if (quality == 100) {
            // At quality 100 we use a matrix of ones -> quantization becomes identity.
            return new Matrix(blockSize, blockSize, 1);
        }

        // Use luma matrix for Y, chroma matrix for Cb/Cr.
        double[][] base = matrixY ? quantizationMatrix8Y : quantizationMatrix8C;

        // Resize 8x8 base matrix to blockSize x blockSize using nearest-neighbor.
        double[][] resized = new double[blockSize][blockSize];
        for (int i = 0; i < blockSize; i++) {
            for (int j = 0; j < blockSize; j++) {
                resized[i][j] = base[i * 8 / blockSize][j * 8 / blockSize];
            }
        }

        // JPEG-style quality scaling: lower quality means stronger quantization.
        double alpha;
        if (quality < 50) {
            alpha = 50.0 / quality;
        } else {
            alpha = 2.0 - 2.0 * quality / 100.0;
        }

        // Scale every quantization step by alpha.
        for (int i = 0; i < blockSize; i++) {
            for (int j = 0; j < blockSize; j++) {
                resized[i][j] = resized[i][j] * alpha;
            }
        }

        return new Matrix(resized);
    }

    // Forward quantization per block: divide each coeff by the quant step and round.
    public static Matrix quantize(Matrix input, int blockSize, int quality, boolean matrixY) {
        if (quality == 100) {
            // No information loss at quality 100 (just return the input as-is).
            return input.copy();
        }
        Matrix quantMatrix = getQuantizationMatrix(blockSize, quality, matrixY);
        int rows = input.getRowDimension();
        int cols = input.getColumnDimension();
        Matrix result = input.copy();

        for (int row = 0; row + blockSize <= rows; row += blockSize) {
            for (int col = 0; col + blockSize <= cols; col += blockSize) {
                Matrix block = input.getMatrix(row, row + blockSize - 1, col, col + blockSize - 1);
                double[][] blockData = block.getArray();
                double[][] quantData = quantMatrix.getArray();
                double[][] quantized = new double[blockSize][blockSize];

                for (int i = 0; i < blockSize; i++) {
                    for (int j = 0; j < blockSize; j++) {
                        // Forward quantization: coefficient / step, then rounded -> lossy stage.
                        // A larger step value in the quantization table means the coefficient is
                        // divided by a bigger number, producing a smaller quotient.
                        double value = blockData[i][j] / quantData[i][j];
                        // How lossy the quantization is depends on the customRound() method.
                        quantized[i][j] = customRound(value);
                    }
                }

                // Write processed block back to the same position in the output matrix.
                result.setMatrix(row, row + blockSize - 1, col, col + blockSize - 1, new Matrix(quantized));
            }
        }

        return result;
    }

    // Dequantization per block: multiply each quantized coeff back by its quant step
    // to get approximate transform-domain values. After this you'd run inverse transform
    // (e.g. IDCT) to get pixels back. It's only an approximation since rounding threw info away.
    public static Matrix inverseQuantize(Matrix input, int blockSize, int quality, boolean matrixY) {
        if (quality == 100) {
            return input.copy();
        }
        Matrix quantMatrix = getQuantizationMatrix(blockSize, quality, matrixY); // Reconstruct from quality parameter as well
        int rows = input.getRowDimension();
        int cols = input.getColumnDimension();
        Matrix result = input.copy();

        for (int row = 0; row + blockSize <= rows; row += blockSize) {
            for (int col = 0; col + blockSize <= cols; col += blockSize) {
                Matrix block = input.getMatrix(row, row + blockSize - 1, col, col + blockSize - 1);
                Matrix dequantized = block.arrayTimes(quantMatrix);
                result.setMatrix(row, row + blockSize - 1, col, col + blockSize - 1, dequantized);
            }
        }

        return result;
    }

    // Rounding strategy for quantized coefficients.
    // In real JPEG the block gets read in zigzag order (low -> high freq) then RLE + Huffman coded.
    private static double customRound(double value) {
        // Small values get killed to zero -- more zeros = longer RLE runs = smaller file.
        if (value >= -0.1 && value <= 0.1) {
            return 0.0;
        } else {
            // One decimal -- more compression than keeping full precision.
            return Math.round(value * 10.0) / 10.0;
        }
    }
}
