package watermark;

import java.awt.image.BufferedImage;
import java.util.Random;

import Jama.Matrix;

// Patchwork watermarking -- a statistical spatial-domain technique.
//
// Based on: W. Bender, D. Gruhl, N. Morimoto, A. Lu,
// "Techniques for Data Hiding,"
// IBM Systems Journal, vol. 35, nos. 3&4, pp. 313-336, 1996.
// https://ieeexplore.ieee.org/document/5387338
//
// How it works:
//   1. For each watermark bit, pick two random sets of pixels (A and B) using a key.
//   2. Embedding:
//        bit=1 -> increase set A by +delta, decrease set B by -delta
//        bit=0 -> decrease set A by -delta, increase set B by +delta
//   3. Extraction:
//        Compute S = mean(A) - mean(B) for each bit.
//        If S > 0 -> bit=1, else bit=0.
//
// The idea is that the average brightness difference between the two patches
// encodes the watermark bit. It's simple and statistically robust against
// uniform noise and JPEG compression, but weak against geometric transforms.
//
// delta controls visibility vs robustness -- try 3-20.
public class PatchworkWatermark {

    // Pixels per patch set (A and B) for each watermark bit.
    private static final int PATCH_SIZE = 50;

    // Embed watermark into a channel (Y recommended).
    public static void embed(Matrix channel, BufferedImage watermark, double delta, int key, boolean multiInsert) {
        int rows = channel.getRowDimension();
        int cols = channel.getColumnDimension();
        int totalPixels = rows * cols;

        int[] wmBits = binarize(watermark);
        int wmSize = wmBits.length;

        int patchSize = Math.min(PATCH_SIZE, totalPixels / (2 * wmSize));
        if (patchSize < 1) patchSize = 1;

        Random rng = new Random(key);

        int iterations = multiInsert ? 3 : 1; // repeat embedding for robustness
        for (int iter = 0; iter < iterations; iter++) {
            Random iterRng = new Random(key + iter * 9999L);
            for (int b = 0; b < wmSize; b++) {
                double sign = (wmBits[b] == 1) ? 1.0 : -1.0;

                // Pick random pixel positions for sets A and B.
                for (int p = 0; p < patchSize; p++) {
                    int idxA = iterRng.nextInt(totalPixels);
                    int idxB = iterRng.nextInt(totalPixels);

                    int rowA = idxA / cols, colA = idxA % cols;
                    int rowB = idxB / cols, colB = idxB % cols;

                    double valA = channel.get(rowA, colA) + sign * delta;
                    double valB = channel.get(rowB, colB) - sign * delta;

                    channel.set(rowA, colA, Math.max(0, Math.min(255, valA)));
                    channel.set(rowB, colB, Math.max(0, Math.min(255, valB)));
                }
            }
        }
    }

    // Extract watermark by checking the statistical difference between patch sets.
    public static BufferedImage extract(Matrix channel, int wmWidth, int wmHeight,
                                        double delta, int key, boolean multiInsert) {
        int rows = channel.getRowDimension();
        int cols = channel.getColumnDimension();
        int totalPixels = rows * cols;
        int wmSize = wmWidth * wmHeight;

        int patchSize = Math.min(PATCH_SIZE, totalPixels / (2 * wmSize));
        if (patchSize < 1) patchSize = 1;

        double[] score = new double[wmSize];

        int iterations = multiInsert ? 3 : 1;
        for (int iter = 0; iter < iterations; iter++) {
            Random iterRng = new Random(key + iter * 9999L);
            for (int b = 0; b < wmSize; b++) {
                double sumA = 0, sumB = 0;
                for (int p = 0; p < patchSize; p++) {
                    int idxA = iterRng.nextInt(totalPixels);
                    int idxB = iterRng.nextInt(totalPixels);

                    int rowA = idxA / cols, colA = idxA % cols;
                    int rowB = idxB / cols, colB = idxB % cols;

                    sumA += channel.get(rowA, colA);
                    sumB += channel.get(rowB, colB);
                }
                // S = mean(A) - mean(B); positive -> bit was 1
                score[b] += (sumA - sumB) / patchSize;
            }
        }

        BufferedImage result = new BufferedImage(wmWidth, wmHeight, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < wmHeight; y++) {
            for (int x = 0; x < wmWidth; x++) {
                int b = y * wmWidth + x;
                int bit = (b < wmSize && score[b] > 0) ? 1 : 0;
                int val = (bit == 1) ? 255 : 0;
                result.setRGB(x, y, (val << 16) | (val << 8) | val);
            }
        }
        return result;
    }

    private static int[] binarize(BufferedImage watermark) {
        int w = watermark.getWidth(), h = watermark.getHeight();
        int[] bits = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                bits[y * w + x] = ((watermark.getRGB(x, y) & 0xFF) > 128) ? 1 : 0;
        return bits;
    }
}
