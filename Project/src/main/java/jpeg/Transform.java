package jpeg;

import Jama.Matrix;
import enums.TransformType;

public class Transform {

    public static Matrix getTransformMatrix(TransformType type, int blockSize) {
        validateBlockSize(blockSize);
        return switch (type) {
            case DCT -> createDctMatrix(blockSize);
            case WHT -> createWhtMatrix(blockSize);
        };
    }

    public static Matrix transform(Matrix input, TransformType type, int blockSize) {
        Matrix transformMatrix = getTransformMatrix(type, blockSize);
        return processByBlocks(input, transformMatrix, false);
    }

    public static Matrix inverseTransform(Matrix input, TransformType type, int blockSize) {
        Matrix transformMatrix = getTransformMatrix(type, blockSize);
        return processByBlocks(input, transformMatrix, true);
    }

    private static Matrix processByBlocks(Matrix input, Matrix transformMatrix, boolean inverse) {
        int blockSize = transformMatrix.getRowDimension();
        int rows = input.getRowDimension();
        int cols = input.getColumnDimension();
        Matrix result = input.copy();

        for (int row = 0; row + blockSize <= rows; row += blockSize) {
            for (int col = 0; col + blockSize <= cols; col += blockSize) {
                Matrix block = input.getMatrix(row, row + blockSize - 1, col, col + blockSize - 1);
                Matrix transformedBlock = inverse
                        ? transformMatrix.transpose().times(block).times(transformMatrix)
                        : transformMatrix.times(block).times(transformMatrix.transpose());
                result.setMatrix(row, row + blockSize - 1, col, col + blockSize - 1, transformedBlock);
            }
        }

        return result;
    }

    private static Matrix createDctMatrix(int blockSize) {
        double[][] matrix = new double[blockSize][blockSize];
        double factor0 = Math.sqrt(1.0 / blockSize);
        double factor = Math.sqrt(2.0 / blockSize);

        for (int i = 0; i < blockSize; i++) {
            double scale = (i == 0) ? factor0 : factor;
            for (int j = 0; j < blockSize; j++) {
                matrix[i][j] = scale * Math.cos(((2.0 * j + 1.0) * i * Math.PI) / (2.0 * blockSize));
            }
        }

        return new Matrix(matrix);
    }

    private static Matrix createWhtMatrix(int blockSize) {
        if (!isPowerOfTwo(blockSize)) {
            throw new IllegalArgumentException("WHT requires block size to be a power of 2.");
        }

        Matrix hadamard = createHadamardMatrix(blockSize);
        return hadamard.times(1.0 / Math.sqrt(blockSize));
    }

    private static Matrix createHadamardMatrix(int size) {
        if (size == 1) {
            return new Matrix(new double[][]{{1.0}});
        }

        Matrix half = createHadamardMatrix(size / 2);
        int halfSize = size / 2;
        Matrix result = new Matrix(size, size);

        for (int i = 0; i < halfSize; i++) {
            for (int j = 0; j < halfSize; j++) {
                double value = half.get(i, j);
                result.set(i, j, value);
                result.set(i, j + halfSize, value);
                result.set(i + halfSize, j, value);
                result.set(i + halfSize, j + halfSize, -value);
            }
        }

        return result;
    }

    private static void validateBlockSize(int blockSize) {
        if (blockSize < 2) {
            throw new IllegalArgumentException("Block size must be at least 2.");
        }
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }
}
