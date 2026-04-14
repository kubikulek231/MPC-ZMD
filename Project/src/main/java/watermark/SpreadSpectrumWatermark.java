package watermark;

import java.awt.image.BufferedImage;
import java.util.Random;

import Jama.Matrix;

// Additive Spread Spectrum watermarking in the spatial domain.
//
// Based on: I.J. Cox, J. Kilian, F.T. Leighton, T. Shamoon,
// "Secure Spread Spectrum Watermarking for Multimedia,"
// IEEE Transactions on Image Processing, vol. 6, no. 12, pp. 1673-1687, Dec. 1997.
// https://ieeexplore.ieee.org/document/650120
//
// How it works:
//   1. Binarize the watermark into bits (white=1, black=0).
//   2. For each watermark bit, generate a pseudo-random noise chip sequence
//      from the key. The chip length = number of pixels per watermark bit.
//   3. Embedding: for bit=1, add +alpha * noise[i] to each pixel in the chip.
//                 for bit=0, add -alpha * noise[i].
//      'alpha' controls the trade-off between invisibility and robustness.
//   4. Extraction: correlate the (possibly attacked) channel with the same noise.
//      If correlation > 0 -> bit=1, else bit=0.
//      With multiInsert, same bit is embedded multiple times and majority-voted.
//
// This is a spread-spectrum technique because each single bit is "spread" across
// many pixels using a wide-band pseudo-random sequence, making it robust against
// localized attacks (crop, noise) but sensitive to geometric attacks (rotation, resize).
public class SpreadSpectrumWatermark {

    // Need at least this many pixels per watermark bit for the correlation to work.
    // With fewer chips, the original pixel values drown out the watermark signal.
    private static final int MIN_CHIPS_PER_BIT = 64;

    // Returns max watermark dimensions for the given channel size.
    public static int[] maxWatermarkSize(int channelRows, int channelCols) {
        int totalPixels = channelRows * channelCols;
        int maxBits = totalPixels / MIN_CHIPS_PER_BIT;
        if (maxBits < 1) maxBits = 1;
        int side = (int) Math.sqrt(maxBits);
        return new int[]{ side, side };
    }

    private static BufferedImage resizeImage(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    // Embed watermark into a channel (Y recommended).
    // alpha: embedding strength -- higher = more robust but more visible.
    //   Needed: alpha > std(pixel) / sqrt(chips_per_bit) for reliable blind extraction.
    //   For typical 512x512 image with 64x64 watermark: alpha ~10-50 works well.
    // key: seed for the pseudo-random chip sequence.
    public static void embed(Matrix channel, BufferedImage watermark, double alpha, int key, boolean multiInsert) {
        int rows = channel.getRowDimension();
        int cols = channel.getColumnDimension();
        int totalPixels = rows * cols;

        // Auto-resize if watermark is too large for meaningful spreading.
        int[] maxWm = maxWatermarkSize(rows, cols);
        if (watermark.getWidth() > maxWm[0] || watermark.getHeight() > maxWm[1]) {
            watermark = resizeImage(watermark, maxWm[0], maxWm[1]);
        }

        int[] wmBits = binarize(watermark);
        int wmSize = wmBits.length;

        // How many pixels per watermark bit (chip length).
        int chipsPerBit = totalPixels / wmSize;
        if (chipsPerBit < 1) chipsPerBit = 1;

        Random rng = new Random(key);

        // Generate a random permutation of pixel indices so bits are spread across the image.
        int[] pixelOrder = new int[totalPixels];
        for (int i = 0; i < totalPixels; i++) pixelOrder[i] = i;
        for (int i = totalPixels - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = pixelOrder[i]; pixelOrder[i] = pixelOrder[j]; pixelOrder[j] = tmp;
        }

        // For each watermark bit, modulate chipsPerBit pixels with PN noise.
        Random noiseRng = new Random(key + 12345);
        int pixIdx = 0;
        for (int b = 0; b < wmSize; b++) {
            double sign = (wmBits[b] == 1) ? 1.0 : -1.0;
            for (int c = 0; c < chipsPerBit && pixIdx < totalPixels; c++, pixIdx++) {
                int idx = pixelOrder[pixIdx];
                int row = idx / cols;
                int col = idx % cols;
                double noise = noiseRng.nextGaussian();
                double val = channel.get(row, col) + sign * alpha * noise;
                channel.set(row, col, Math.max(0, Math.min(255, val)));
            }
        }

        // multiInsert: repeat embedding with remaining pixels
        if (multiInsert) {
            while (pixIdx + chipsPerBit <= totalPixels) {
                int b = (pixIdx / chipsPerBit) % wmSize;
                double sign = (wmBits[b] == 1) ? 1.0 : -1.0;
                for (int c = 0; c < chipsPerBit && pixIdx < totalPixels; c++, pixIdx++) {
                    int idx = pixelOrder[pixIdx];
                    int row = idx / cols;
                    int col = idx % cols;
                    double noise = noiseRng.nextGaussian();
                    double val = channel.get(row, col) + sign * alpha * noise;
                    channel.set(row, col, Math.max(0, Math.min(255, val)));
                }
            }
        }
    }

    // Extract watermark by correlating with the same PN sequence.
    // Uses mean-removed correlation: subtracts the channel mean before correlating
    // to eliminate the DC bias that would otherwise drown out the watermark signal.
    public static BufferedImage extract(Matrix channel, int wmWidth, int wmHeight,
                                        double alpha, int key, boolean multiInsert) {
        int rows = channel.getRowDimension();
        int cols = channel.getColumnDimension();
        int totalPixels = rows * cols;
        int wmSize = wmWidth * wmHeight;

        int chipsPerBit = totalPixels / wmSize;
        if (chipsPerBit < 1) chipsPerBit = 1;

        // Compute channel mean to remove DC bias before correlation.
        double channelMean = 0;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                channelMean += channel.get(r, c);
        channelMean /= totalPixels;

        Random rng = new Random(key);
        int[] pixelOrder = new int[totalPixels];
        for (int i = 0; i < totalPixels; i++) pixelOrder[i] = i;
        for (int i = totalPixels - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = pixelOrder[i]; pixelOrder[i] = pixelOrder[j]; pixelOrder[j] = tmp;
        }

        double[] correlation = new double[wmSize];
        int[] count = new int[wmSize];

        Random noiseRng = new Random(key + 12345);
        int pixIdx = 0;

        int totalChips = multiInsert ? totalPixels : wmSize * chipsPerBit;
        totalChips = Math.min(totalChips, totalPixels);

        for (int p = 0; p < totalChips; p++, pixIdx++) {
            if (pixIdx >= totalPixels) break;
            int b = (p / chipsPerBit) % wmSize;
            int idx = pixelOrder[pixIdx];
            int row = idx / cols;
            int col = idx % cols;
            double noise = noiseRng.nextGaussian();
            // Mean-removed correlation: (pixel - mean) * noise
            // This removes the DC component that would dominate the raw correlation.
            correlation[b] += (channel.get(row, col) - channelMean) * noise;
            count[b]++;
        }

        // Build output image.
        BufferedImage result = new BufferedImage(wmWidth, wmHeight, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < wmHeight; y++) {
            for (int x = 0; x < wmWidth; x++) {
                int b = y * wmWidth + x;
                int bit = (b < wmSize && correlation[b] > 0) ? 1 : 0;
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
