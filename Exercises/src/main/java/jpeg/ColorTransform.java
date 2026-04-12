package jpeg;

import Jama.Matrix;

public class ColorTransform {

    // RGB -> YCbCr using SDTV (BT.601 limited range) coefficients from the assignment
    public static Matrix[] convertOriginalRGBtoYcBcR(int[][] red, int[][] green, int[][] blue) {
        int rows = red.length;
        int cols = red[0].length;

        Matrix convertedY  = new Matrix(rows, cols);
        Matrix convertedCb = new Matrix(rows, cols);
        Matrix convertedCr = new Matrix(rows, cols);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double r = red[i][j];
                double g = green[i][j];
                double b = blue[i][j];

                convertedY.set(i, j,   0.257 * r + 0.504 * g + 0.098 * b + 16.0);
                convertedCb.set(i, j, -0.148 * r - 0.291 * g + 0.439 * b + 128.0);
                convertedCr.set(i, j,  0.439 * r - 0.368 * g - 0.071 * b + 128.0);
            }
        }

        return new Matrix[]{convertedY, convertedCb, convertedCr};
    }

    // YCbCr -> RGB, returns Object[] so each element can be cast to int[][] by the test
    public static Object[] convertModifiedYcBcRtoRGB(Matrix Y, Matrix Cb, Matrix Cr) {
        int rows = Y.getRowDimension();
        int cols = Y.getColumnDimension();

        int[][] convertedRed   = new int[rows][cols];
        int[][] convertedGreen = new int[rows][cols];
        int[][] convertedBlue  = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double y  = Y.get(i, j)  - 16.0;
                double cb = Cb.get(i, j) - 128.0;
                double cr = Cr.get(i, j) - 128.0;

                convertedRed[i][j]   = clamp((int) Math.round(1.164 * y + 1.596 * cr));
                convertedGreen[i][j] = clamp((int) Math.round(1.164 * y - 0.813 * cr - 0.391 * cb));
                convertedBlue[i][j]  = clamp((int) Math.round(1.164 * y + 2.018 * cb));
            }
        }

        return new Object[]{convertedRed, convertedGreen, convertedBlue};
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
