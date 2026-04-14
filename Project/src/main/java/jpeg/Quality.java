package jpeg;

import Jama.Matrix;

/**
 * Image quality metrics.
 * Method names match test expectations:
 *   countMSE / countMAE / countSAE / countPSNR / countPSNRforRGB
 *   countSSIM / countMSSIM
 */
public class Quality {

    // ===== MSE – Mean Squared Error =====
    /**
     * MSE = (1 / M*N) * sum( (x - x')^2 )
     * Lower is better.
     */
    public static double countMSE(double[][] original, double[][] modified) {
        int M = original.length;
        int N = original[0].length;
        double sum = 0.0;
        for (int m = 0; m < M; m++) {
            for (int n = 0; n < N; n++) {
                double diff = original[m][n] - modified[m][n];
                sum += diff * diff;
            }
        }
        return sum / ((double) M * N);
    }

    // ===== MAE – Mean Absolute Error =====
    /**
     * MAE = (1 / M*N) * sum( |x - x'| )
     * Lower is better.
     */
    public static double countMAE(double[][] original, double[][] modified) {
        int M = original.length;
        int N = original[0].length;
        double sum = 0.0;
        for (int m = 0; m < M; m++) {
            for (int n = 0; n < N; n++) {
                sum += Math.abs(original[m][n] - modified[m][n]);
            }
        }
        return sum / ((double) M * N);
    }

    // ===== SAE – Sum of Absolute Errors =====
    /**
     * SAE = sum( |x - x'| )
     * Lower is better.
     */
    public static double countSAE(double[][] original, double[][] modified) {
        int M = original.length;
        int N = original[0].length;
        double sum = 0.0;
        for (int m = 0; m < M; m++) {
            for (int n = 0; n < N; n++) {
                sum += Math.abs(original[m][n] - modified[m][n]);
            }
        }
        return sum;
    }

    // ===== PSNR – Peak Signal-to-Noise Ratio =====
    /**
     * PSNR = 10 * log10( 255^2 / MSE )
     * Higher is better. Returns +inf when MSE == 0 (identical images).
     */
    public static double countPSNR(double mse) {
        if (mse == 0.0) return Double.POSITIVE_INFINITY;
        return 10.0 * Math.log10((255.0 * 255.0) / mse);
    }

    /**
     * PSNR for an RGB image: average the three per-channel MSEs, then compute PSNR on that.
     */
    public static double countPSNRforRGB(int mseR, int mseG, int mseB) {
        double avgMSE = (mseR + mseG + mseB) / 3.0;
        return countPSNR(avgMSE);
    }

    // ===== SSIM – Structural Similarity Index =====
    /**
     * SSIM over the full luminance channel (as a Jama Matrix).
     *
     * SSIM(x,y) = (2*muX*muY + C1)(2*sigmaXY + C2) / (muX^2 + muY^2 + C1)(sigmaX^2 + sigmaY^2 + C2)
     *
     * C1 = (K1*L)^2, C2 = (K2*L)^2, K1 = 0.01, K2 = 0.03, L = 255.
     * Variances use Bessel's correction (N-1 denominator).
     */
    public static double countSSIM(Matrix original, Matrix modified) {
        double[][] orig = original.getArray();
        double[][] mod  = modified.getArray();
        return computeSSIM(orig, mod);
    }

    // ===== MSSIM – Mean Structural Similarity Index =====
    /**
     * MSSIM: chop both images into 8x8 patches, compute SSIM for each pair,
     * then average all of them. Partial patches at the edges are skipped.
     */
    public static double countMSSIM(Matrix original, Matrix modified) {
        double[][] orig = original.getArray();
        double[][] mod  = modified.getArray();

        int M = orig.length;
        int N = orig[0].length;
        final int PATCH = 8;

        double sumSSIM = 0.0;
        int count = 0;

        for (int row = 0; row + PATCH <= M; row += PATCH) {
            for (int col = 0; col + PATCH <= N; col += PATCH) {
                double[][] origPatch = extractPatch(orig, row, col, PATCH);
                double[][] modPatch  = extractPatch(mod,  row, col, PATCH);
                sumSSIM += computeSSIM(origPatch, modPatch);
                count++;
            }
        }

        if (count == 0) return computeSSIM(orig, mod);   // image smaller than 8x8
        return sumSSIM / count;
    }

    // ===== Utility =====
    /** Convert int[][] (R/G/B channel) to double[][] for the quality formulas. */
    public static double[][] convertIntToDouble(int[][] intArray) {
        double[][] doubleArray = new double[intArray.length][intArray[0].length];
        for (int i = 0; i < intArray.length; i++) {
            for (int j = 0; j < intArray[0].length; j++) {
                doubleArray[i][j] = (double) intArray[i][j];
            }
        }
        return doubleArray;
    }

    // ===== Private helpers =====
    /** Core SSIM math on raw double[][] arrays. */
    private static double computeSSIM(double[][] orig, double[][] mod) {
        int M = orig.length;
        int N = orig[0].length;
        int totalPixels = M * N;

        // Means (mu)
        double muX = 0.0, muY = 0.0;
        for (int m = 0; m < M; m++) {
            for (int n = 0; n < N; n++) {
                muX += orig[m][n];
                muY += mod[m][n];
            }
        }
        muX /= totalPixels;
        muY /= totalPixels;

        // Variances and covariance -- Bessel's correction (N-1 denominator)
        double sigmaX2 = 0.0, sigmaY2 = 0.0, sigmaXY = 0.0;
        for (int m = 0; m < M; m++) {
            for (int n = 0; n < N; n++) {
                double dx = orig[m][n] - muX;
                double dy = mod[m][n]  - muY;
                sigmaX2 += dx * dx;
                sigmaY2 += dy * dy;
                sigmaXY += dx * dy;
            }
        }
        double bessel = totalPixels - 1.0;
        sigmaX2 /= bessel;
        sigmaY2 /= bessel;
        sigmaXY /= bessel;

        // Stabilisation constants: C1 = (K1*L)^2, C2 = (K2*L)^2, L=255
        final double C1 = (0.01 * 255.0) * (0.01 * 255.0);
        final double C2 = (0.03 * 255.0) * (0.03 * 255.0);

        double numerator   = (2.0 * muX * muY + C1) * (2.0 * sigmaXY  + C2);
        double denominator = (muX * muX + muY * muY + C1) * (sigmaX2 + sigmaY2 + C2);

        return numerator / denominator;
    }

    private static double[][] extractPatch(double[][] src, int startRow, int startCol, int size) {
        double[][] patch = new double[size][size];
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                patch[r][c] = src[startRow + r][startCol + c];
        return patch;
    }
}
