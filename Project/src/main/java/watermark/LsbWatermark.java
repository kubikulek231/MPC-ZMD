package watermark;

import java.awt.image.BufferedImage;
import java.util.Random;

import Jama.Matrix;

// LSB watermarking -- works in the spatial domain on a single YCbCr channel.
// The watermark is a B/W image, gets binarized and then scrambled with a key.
public class LsbWatermark {

    // Embed a binary watermark into a channel at bit plane h.
    // channel: Matrix(height, width), modified in-place.
    // h: which bit plane (0 = LSB, 7 = MSB). The assignment uses 1-based naming where h=1 is LSB.
    // key: seed for scrambling the watermark bits before embedding.
    // strength: if > 0, nudges pixel values to make the embedded bit harder to destroy.
    // multiInsert: tiles the watermark across the whole channel for redundancy.
    public static void embed(Matrix channel, BufferedImage watermark, int h, int key, int strength, boolean multiInsert) {
        int chHeight = channel.getRowDimension();
        int chWidth = channel.getColumnDimension();

        // Binarize watermark: pixel > 128 -> 1, else 0.
        int wmW = watermark.getWidth();
        int wmH = watermark.getHeight();
        int[] wmBits = new int[wmW * wmH];
        for (int y = 0; y < wmH; y++) {
            for (int x = 0; x < wmW; x++) {
                int gray = (watermark.getRGB(x, y) & 0xFF);
                wmBits[y * wmW + x] = (gray > 128) ? 1 : 0;
            }
        }

        // Permute watermark bits with the key so they're not stored in order.
        int[] permuted = permute(wmBits, key);

        int totalPixels = chHeight * chWidth;
        // Build embedding sequence -- tile if multiInsert is on.
        int[] embedBits;
        if (multiInsert && permuted.length < totalPixels) {
            embedBits = new int[totalPixels];
            for (int i = 0; i < totalPixels; i++) {
                embedBits[i] = permuted[i % permuted.length];
            }
        } else {
            embedBits = permuted;
        }

        int count = Math.min(embedBits.length, totalPixels);
        int bitMask = 1 << h;
        int clearMask = ~bitMask;

        for (int i = 0; i < count; i++) {
            int row = i / chWidth;
            int col = i % chWidth;
            // Clamp to 0-255 integer range for bit manipulation.
            int pixel = clamp((int) Math.round(channel.get(row, col)));

            // Clear the target bit plane.
            pixel = pixel & clearMask;

            // Place the watermark bit into the target bit plane.
            pixel = pixel | (embedBits[i] << h);

            // Apply strength: bump the pixel value a bit to make the embedded bit stick better.
            if (strength > 0) {
                int half = strength / 2;
                if (embedBits[i] == 1) {
                    pixel = Math.min(255, pixel + half);
                } else {
                    pixel = Math.max(0, pixel - half);
                }
            }

            channel.set(row, col, pixel);
        }
    }

    // Read the watermark back from a channel.
    // Uses the same h, key and multiInsert as during embedding.
    // With multiInsert we majority-vote all copies of each bit.
    public static BufferedImage extract(Matrix channel, int wmWidth, int wmHeight, int h, int key, boolean multiInsert) {
        int chHeight = channel.getRowDimension();
        int chWidth = channel.getColumnDimension();
        int totalPixels = chHeight * chWidth;
        int wmSize = wmWidth * wmHeight;

        int[] rawBits;
        if (multiInsert && wmSize < totalPixels) {
            // Majority voting: grab all copies and pick the most common value per bit position.
            int[] sum = new int[wmSize];
            int[] cnt = new int[wmSize];
            for (int i = 0; i < totalPixels; i++) {
                int row = i / chWidth;
                int col = i % chWidth;
                int pixel = clamp((int) Math.round(channel.get(row, col)));
                int bit = (pixel >> h) & 1;
                sum[i % wmSize] += bit;
                cnt[i % wmSize]++;
            }
            rawBits = new int[wmSize];
            for (int i = 0; i < wmSize; i++) {
                rawBits[i] = (sum[i] * 2 >= cnt[i]) ? 1 : 0;
            }
        } else {
            int count = Math.min(wmSize, totalPixels);
            rawBits = new int[count];
            for (int i = 0; i < count; i++) {
                int row = i / chWidth;
                int col = i % chWidth;
                int pixel = clamp((int) Math.round(channel.get(row, col)));
                rawBits[i] = (pixel >> h) & 1;
            }
        }

        // Un-scramble the extracted bits.
        int[] recovered = inversePermute(rawBits, key);

        // Turn binary bits back into an image.
        BufferedImage result = new BufferedImage(wmWidth, wmHeight, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < wmHeight; y++) {
            for (int x = 0; x < wmWidth; x++) {
                int idx = y * wmWidth + x;
                int val = (idx < recovered.length && recovered[idx] == 1) ? 255 : 0;
                int rgb = (val << 16) | (val << 8) | val;
                result.setRGB(x, y, rgb);
            }
        }
        return result;
    }

    // Fisher-Yates shuffle with the given seed.
    static int[] permute(int[] bits, int key) {
        int[] result = bits.clone();
        Random rng = new Random(key);
        for (int i = result.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }
        return result;
    }

    // Undo the Fisher-Yates shuffle.
    static int[] inversePermute(int[] bits, int key) {
        int n = bits.length;
        // Reconstruct the swap sequence.
        Random rng = new Random(key);
        int[] swaps = new int[n];
        for (int i = n - 1; i > 0; i--) {
            swaps[i] = rng.nextInt(i + 1);
        }
        // Apply swaps in reverse order.
        int[] result = bits.clone();
        for (int i = 1; i < n; i++) {
            int j = swaps[i];
            int tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }
        return result;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
