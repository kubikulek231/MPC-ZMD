package imageProcessing;

import Jama.Matrix;

/**
 * Color space conversion (legacy version kept for the tests).
 */
public class ColorTransform {

	public static Matrix[] convertOriginalRGBtoYcBcR(int[][] red, int[][] green, int[][] blue) {
		int width = red.length;
		int height = red[0].length;

		Matrix convertedY = new Matrix(width, height);
		Matrix convertedCb = new Matrix(width, height);
		Matrix convertedCr = new Matrix(width, height);

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				double r = red[x][y];
				double g = green[x][y];
				double b = blue[x][y];

				convertedY.set(x, y, 0.257 * r + 0.504 * g + 0.098 * b + 16.0);
				convertedCb.set(x, y, -0.148 * r - 0.291 * g + 0.439 * b + 128.0);
				convertedCr.set(x, y, 0.439 * r - 0.368 * g - 0.071 * b + 128.0);
			}
		}

		return new Matrix[]{convertedY, convertedCb, convertedCr};
	}

	public static int[][][] convertModifiedYcBcRtoRGB(Matrix Y, Matrix Cb, Matrix Cr) {
		int width = Y.getRowDimension();
		int height = Y.getColumnDimension();

		int[][] convertedRed = new int[width][height];
		int[][] convertedGreen = new int[width][height];
		int[][] convertedBlue = new int[width][height];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				double yValue = Y.get(x, y) - 16.0;
				double cbValue = Cb.get(x, y) - 128.0;
				double crValue = Cr.get(x, y) - 128.0;

				convertedRed[x][y] = clamp((int) Math.round(1.164 * yValue + 1.596 * crValue));
				convertedGreen[x][y] = clamp((int) Math.round(1.164 * yValue - 0.813 * crValue - 0.391 * cbValue));
				convertedBlue[x][y] = clamp((int) Math.round(1.164 * yValue + 2.018 * cbValue));
			}
		}

		return new int[][][]{convertedRed, convertedGreen, convertedBlue};
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}

}
