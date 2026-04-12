package tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;

import Jama.Matrix;
import jpeg.ColorTransform;

public class ColorTransformTest {
    int[][] testRed = {{255, 25, 88, 209, 51, 182}, {1, 90, 161, 30, 210, 124}, {255, 232, 103, 190, 140, 75}, {205, 10, 110, 170, 60, 227}, {95, 170, 58, 189, 249, 29}, {37, 255, 85, 122, 190, 131}};
    int[][] testGreen = {{255, 50, 110, 175, 236, 40}, {1, 155, 210, 40, 100, 200}, {0, 66, 190, 223, 70, 144}, {253, 80, 30, 202, 177, 10}, {111, 222, 60, 140, 90, 255}, {177, 20, 184, 121, 80, 138}};
    int[][] testBlue = {{255, 82, 130, 231, 50, 112}, {0, 10, 60, 193, 140, 250}, {0, 210, 170, 40, 228, 104}, {52, 160, 128, 73, 240, 38}, {180, 110, 222, 20, 150, 89}, {195, 254, 30, 140, 200, 77}};

    double[][] testY = {{235.05, 55.66, 106.8, 180.55, 152.95, 93.91}, {16.76, 118.23, 169.1, 62.78, 134.09, 173.17}, {81.54, 129.47, 154.89, 181.14, 109.6, 118.04}, {201.29, 74.57, 71.93, 168.65, 144.15, 83.1}, {114.0, 182.36, 82.9, 137.09, 140.05, 160.7}, {133.83, 116.51, 133.52, 122.06, 124.75, 126.77}};
    double[][] testCb = {{128.0, 145.75, 140.04, 147.55, 73.73, 138.59}, {127.56, 73.97, 69.4, 196.65, 129.28, 161.2}, {90.26, 166.65, 132.1, 52.55, 187.0, 120.65}, {46.87, 173.48, 159.18, 76.11, 172.97, 108.18}, {160.66, 86.53, 199.41, 68.07, 130.81, 88.57}, {156.62, 195.95, 75.05, 136.19, 164.4, 102.26}};
    double[][] testCr = {{128.0, 114.75, 116.92, 138.95, 59.99, 185.23}, {128.07, 109.76, 117.14, 112.75, 173.45, 91.09}, {239.95, 190.65, 91.23, 126.51, 147.51, 100.55}, {121.2, 91.59, 156.16, 123.11, 72.16, 221.28}, {116.08, 113.12, 115.62, 158.03, 193.54, 40.57}, {65.26, 214.55, 95.47, 127.09, 167.77, 129.26}};

    @Test
    void convertOriginalRGBtoYcBcRTest() {
        Matrix[] matrixYcBcR = ColorTransform.convertOriginalRGBtoYcBcR(testRed, testGreen, testBlue);

        assertArrayEquals(testY, this.roundArray(matrixYcBcR[0]), "Error: Component Y is different than expected. In jpeg.ColorTransform class check your calculations in method convertOriginalRGBtoYcBcRt()");
        assertArrayEquals(testCb, this.roundArray(matrixYcBcR[1]), "Error: Component Cb is different than expected. In jpeg.ColorTransform class check your calculations in method convertOriginalRGBtoYcBcRt()");
        assertArrayEquals(testCr, this.roundArray(matrixYcBcR[2]), "Error: Component Cr is different than expected. In jpeg.ColorTransform class check your calculations in method convertOriginalRGBtoYcBcRt()");

        System.out.println("Test for conversion from RGB to YCbCr: OK");
    }

    @Test
    void convertModifiedYcBcRtoRGBTest() {
        Object[] ModifiedYcBcRtoRGB = ColorTransform.convertModifiedYcBcRtoRGB(new Matrix(testY), new Matrix(testCb), new Matrix(testCr));

        assertArrayEquals(testRed, (int[][]) ModifiedYcBcRtoRGB[0], "Error: There is probably an error in the calculation. In the jpeg.ColorTransform class, check the convertModifiedYcBcRtoRGB method.");
        assertArrayEquals(testGreen, (int[][]) ModifiedYcBcRtoRGB[1], "Error: There is probably an error in the calculation. In the jpeg.ColorTransform class, check the convertModifiedYcBcRtoRGB method.");
        assertArrayEquals(testBlue, (int[][]) ModifiedYcBcRtoRGB[2], "Error: There is probably an error in the calculation. In the jpeg.ColorTransform class, check the convertModifiedYcBcRtoRGB method.");

        double[][] testEdgeY = {{84}};
        double[][] testEdgeCb = {{80}};
        double[][] testEdgeCr = {{245}};

        int[][] testEdgeRed = {{255}};
        int[][] testEdgeGreen = {{3}};
        int[][] testEdgeBlue = {{0}};

        Object[] ModifiedYcBcRtoRGBEdge = ColorTransform.convertModifiedYcBcRtoRGB(new Matrix(testEdgeY), new Matrix(testEdgeCb), new Matrix(testEdgeCr));

        assertArrayEquals(testEdgeRed, (int[][]) ModifiedYcBcRtoRGBEdge[0], "Error: Probably values greater than 255 are not handled. Check the method convertModifiedYcBcRtoRGB in the jpeg.ColorTransform class.");
        assertArrayEquals(testEdgeGreen, (int[][]) ModifiedYcBcRtoRGBEdge[1], "Error: Probably there is an error in the calculation (value may be out of the range 0-255). Check the method convertModifiedYcBcRtoRGB in the jpeg.ColorTransform class.");
        assertArrayEquals(testEdgeBlue, (int[][]) ModifiedYcBcRtoRGBEdge[2], "Error: Probably values less than 0 are not handled. Check the method convertModifiedYcBcRtoRGB in the jpeg.ColorTransform class.");

        System.out.println("Test for conversion from YCbCr to RGB: OK");
    }

    private double[][] roundArray(Matrix matrix) {
        var arr = matrix.getArray();
        double[][] result = new double[arr.length][arr[0].length];

        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr[0].length; j++) {
                result[i][j] = Math.round(arr[i][j] * 100.0) / 100.0;
            }
        }
        return result;
    }
}
