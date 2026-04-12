package jpeg;

import Jama.Matrix;

public class Quantization {

    /** Pro jasovou slozku */
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

    /** Pro barvonosne slozky */
    public static final double[][] quantizationMatrix8C = {
        {17, 18, 24, 47, 99, 99, 99, 99},
        {18, 21, 26, 66, 99, 99, 99, 99},
        {24, 26, 56, 99, 99, 99, 99, 99},
        {47, 66, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99},
        {99, 99, 99, 99, 99, 99, 99, 99}
    };

    public double[][] getQuantizationMatrix8Y() {
        return quantizationMatrix8Y;
    }

    public double[][] getQuantizationMatrix8C() {
        return quantizationMatrix8C;
    }

    public static Matrix getQuantizationMatrix(int blockSize, int quality, boolean matrixY) {
        if (quality == 100) {
            return new Matrix(blockSize, blockSize, 1);
        }

        double[][] base = matrixY ? quantizationMatrix8Y : quantizationMatrix8C;

        // Resize 8x8 base matrix to blockSize x blockSize using nearest-neighbor
        double[][] resized = new double[blockSize][blockSize];
        for (int i = 0; i < blockSize; i++) {
            for (int j = 0; j < blockSize; j++) {
                resized[i][j] = base[i * 8 / blockSize][j * 8 / blockSize];
            }
        }

        // Compute alpha from quality factor
        double alpha;
        if (quality < 50) {
            alpha = 50.0 / quality;
        } else {
            alpha = 2.0 - 2.0 * quality / 100.0;
        }

        // Apply alpha
        for (int i = 0; i < blockSize; i++) {
            for (int j = 0; j < blockSize; j++) {
                resized[i][j] = resized[i][j] * alpha;
            }
        }

        return new Matrix(resized);
    }

    public static Matrix quantize(Matrix input, int blockSize, int quality, boolean matrixY) {
        if (quality == 100) {
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
                        double value = blockData[i][j] / quantData[i][j];
                        quantized[i][j] = customRound(value);
                    }
                }

                result.setMatrix(row, row + blockSize - 1, col, col + blockSize - 1, new Matrix(quantized));
            }
        }

        return result;
    }

    public static Matrix inverseQuantize(Matrix input, int blockSize, int quality, boolean matrixY) {
        if (quality == 100) {
            return input.copy();
        }
        Matrix quantMatrix = getQuantizationMatrix(blockSize, quality, matrixY);
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

    private static double customRound(double value) {
        if (value >= -0.1 && value <= 0.1) {
            return 0.0;
        } else {
            return Math.round(value * 10.0) / 10.0;
        }
    }
}
