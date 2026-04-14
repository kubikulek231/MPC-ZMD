package jpeg;

import Jama.Matrix;
import enums.SamplingType;

public class Sampling {

    /**
     * Downsample chroma according to the sampling type.
     */
    public static Matrix sampleDown(Matrix inputMatrix, SamplingType samplingType) {
        return switch (samplingType) {
            case S_4_4_4 -> inputMatrix.copy();
            // Drop every 2nd column: width / 2
            case S_4_2_2 -> downSample(inputMatrix);
            // Drop every 2nd column twice: width / 4
            case S_4_1_1 -> downSample(downSample(inputMatrix));
            // Drop every 2nd column AND every 2nd row (via transpose trick): width/2, height/2
            case S_4_2_0 -> downSample(downSample(inputMatrix.transpose()).transpose());
        };
    }

    /**
     * Upsample chroma back to its original dimensions.
     */
    public static Matrix sampleUp(Matrix inputMatrix, SamplingType samplingType) {
        return switch (samplingType) {
            case S_4_4_4 -> inputMatrix.copy();
            case S_4_2_2 -> upSample(inputMatrix);
            case S_4_1_1 -> upSample(upSample(inputMatrix));
            case S_4_2_0 -> upSample(upSample(inputMatrix.transpose()).transpose());
        };
    }

    /**
     * Keeps every even column (0, 2, 4, ...), so we get half the columns.
     */
    private static Matrix downSample(Matrix mat) {
        int rows = mat.getRowDimension();
        int cols = mat.getColumnDimension();

        int[] rowIndices = new int[rows];
        for (int i = 0; i < rows; i++) rowIndices[i] = i;

        int outCols = (cols + 1) / 2;
        int[] colIndices = new int[outCols];
        for (int i = 0; i < outCols; i++) colIndices[i] = i * 2;

        return mat.getMatrix(rowIndices, colIndices);
    }

    /**
     * Duplicates each column (col 0 -> cols 0 & 1, col 1 -> cols 2 & 3, ...), doubling the width.
     */
    private static Matrix upSample(Matrix mat) {
        int rows = mat.getRowDimension();
        int cols = mat.getColumnDimension();
        Matrix result = new Matrix(rows, cols * 2);

        for (int col = 0; col < cols; col++) {
            Matrix column = mat.getMatrix(0, rows - 1, col, col);
            result.setMatrix(0, rows - 1, col * 2,     col * 2,     column);
            result.setMatrix(0, rows - 1, col * 2 + 1, col * 2 + 1, column);
        }
        return result;
    }
}
