package watermark;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import Jama.Matrix;
import enums.TransformType;
import jpeg.Transform;

// DCT-domain watermarking -- hides a binary watermark by tweaking pairs of
// frequency coefficients in each DCT block.
public class DctWatermark {

    // Returns the maximum watermark dimensions that fit in the given channel
    // with the given block size. One block = one bit, so max pixels = blocksInRow * blocksInCol.
    public static int[] maxWatermarkSize(int channelRows, int channelCols, int blockSize) {
        int blocksInRow = channelCols / blockSize;
        int blocksInCol = channelRows / blockSize;
        return new int[]{ blocksInRow, blocksInCol };
    }

    // Resizes an image using bilinear interpolation.
    private static BufferedImage resizeImage(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    // Embeds the watermark into a single channel (usually Y) by swapping DCT coefficients.
    // channel: Matrix(height, width) -- gets modified in-place.
    // watermark: B/W image, each pixel is one bit (white=1, black=0).
    // blockSize: size of DCT blocks (e.g. 8). One block = one watermark bit.
    // u1,v1,u2,v2: which two DCT coefficients to use.
    //   - Should be somewhere in the mid-frequencies (e.g. (3,4) and (4,3) for 8x8).
    //   - Too close to (0,0) -> visible artifacts. Too close to (N-1,N-1) -> fragile.
    // h: robustness depth -- after swapping we also push the coeffs apart
    //    so |c1 - c2| >= h. Bigger h = harder to destroy but also more visible.
    //    h = 0 means just swap, no extra gap.
    // multiInsert: repeats the watermark across all blocks (tiling). During extraction
    //   each bit gets majority-voted from all its copies, which helps robustness.
    public static void embed(Matrix channel, BufferedImage watermark, int blockSize,
                             int u1, int v1, int u2, int v2, double h, boolean multiInsert) {
        int rows = channel.getRowDimension();
        int cols = channel.getColumnDimension();

        // Auto-resize watermark if it has more bits than available blocks.
        int[] maxSize = maxWatermarkSize(rows, cols, blockSize);
        if (watermark.getWidth() > maxSize[0] || watermark.getHeight() > maxSize[1]) {
            watermark = resizeImage(watermark, maxSize[0], maxSize[1]);
        }

        // Binarize watermark.
        int wmW = watermark.getWidth();
        int wmH = watermark.getHeight();
        int[] wmBits = new int[wmW * wmH];
        for (int y = 0; y < wmH; y++) {
            for (int x = 0; x < wmW; x++) {
                int gray = watermark.getRGB(x, y) & 0xFF;
                wmBits[y * wmW + x] = (gray > 128) ? 1 : 0;
            }
        }

        // Count available blocks.
        int blocksInRow = cols / blockSize;
        int blocksInCol = rows / blockSize;
        int totalBlocks = blocksInRow * blocksInCol;

        // Build DCT transform matrix once.
        Matrix T = Transform.getTransformMatrix(TransformType.DCT, blockSize);
        Matrix Tt = T.transpose();

        int wmIdx = 0;
        for (int br = 0; br < blocksInCol; br++) {
            for (int bc = 0; bc < blocksInRow; bc++) {
                // Which watermark bit to embed in this block.
                int bitIdx;
                if (multiInsert) {
                    bitIdx = wmIdx % wmBits.length;
                } else {
                    if (wmIdx >= wmBits.length) break;
                    bitIdx = wmIdx;
                }

                int startRow = br * blockSize;
                int startCol = bc * blockSize;

                // Forward 2D DCT on the block:
                //   G(i,j) = (1/4)*Ci*Cj * SUM B(x,y)*cos(...)*cos(...)
                // In matrix form: G = T * B * T^T
                Matrix block = channel.getMatrix(startRow, startRow + blockSize - 1,
                        startCol, startCol + blockSize - 1);
                Matrix dctBlock = T.times(block).times(Tt);

                double c1 = dctBlock.get(u1, v1);
                double c2 = dctBlock.get(u2, v2);

                int bit = wmBits[bitIdx];

                // bit 0 -> c1 should be bigger than c2
                // bit 1 -> c1 should be smaller or equal to c2
                // If it already matches we don't touch anything, otherwise swap.
                if (bit == 0) {
                    if (c1 <= c2) {
                        // Swap coefficients.
                        dctBlock.set(u1, v1, c2);
                        dctBlock.set(u2, v2, c1);
                        c1 = dctBlock.get(u1, v1);
                        c2 = dctBlock.get(u2, v2);
                    }
                    // Push them apart by h so the gap doesn't accidentally flip
                    // from small changes like JPEG recompression or noise.
                    if (h > 0 && Math.abs(c1 - c2) <= h) {
                        dctBlock.set(u1, v1, c1 + h / 2.0);
                        dctBlock.set(u2, v2, c2 - h / 2.0);
                    }
                } else {
                    if (c1 > c2) {
                        dctBlock.set(u1, v1, c2);
                        dctBlock.set(u2, v2, c1);
                        c1 = dctBlock.get(u1, v1);
                        c2 = dctBlock.get(u2, v2);
                    }
                    // Same thing but other direction.
                    if (h > 0 && Math.abs(c1 - c2) <= h) {
                        dctBlock.set(u1, v1, c1 - h / 2.0);
                        dctBlock.set(u2, v2, c2 + h / 2.0);
                    }
                }

                // Inverse 2D DCT to get back pixel values:
                //   B(x,y) = (1/4) * SUM Ci*Cj*G(i,j)*cos(...)*cos(...)
                // In matrix form: B = T^T * G * T
                Matrix modified = Tt.times(dctBlock).times(T);
                channel.setMatrix(startRow, startRow + blockSize - 1,
                        startCol, startCol + blockSize - 1, modified);

                wmIdx++;
                if (!multiInsert && wmIdx >= wmBits.length) break;
            }
            if (!multiInsert && wmIdx >= wmBits.length) break;
        }
    }

    // Extracts the watermark back -- blind, doesn't need the original image.
    // Just checks each block: c1 > c2 -> bit 0, otherwise bit 1.
    // With multiInsert, same bit appears in many blocks so we majority-vote them.
    // Returns a B/W image of the given dimensions.
    public static BufferedImage extract(Matrix channel, int wmWidth, int wmHeight, int blockSize,
                                        int u1, int v1, int u2, int v2, boolean multiInsert) {
        int rows = channel.getRowDimension();
        int cols = channel.getColumnDimension();
        int blocksInRow = cols / blockSize;
        int blocksInCol = rows / blockSize;
        int totalBlocks = blocksInRow * blocksInCol;
        int wmSize = wmWidth * wmHeight;

        Matrix T = Transform.getTransformMatrix(TransformType.DCT, blockSize);
        Matrix Tt = T.transpose();

        int[] sum = null;
        int[] cnt = null;
        int[] rawBits = null;

        if (multiInsert) {
            sum = new int[wmSize];
            cnt = new int[wmSize];
        } else {
            rawBits = new int[Math.min(wmSize, totalBlocks)];
        }

        int wmIdx = 0;
        for (int br = 0; br < blocksInCol; br++) {
            for (int bc = 0; bc < blocksInRow; bc++) {
                int startRow = br * blockSize;
                int startCol = bc * blockSize;

                Matrix block = channel.getMatrix(startRow, startRow + blockSize - 1,
                        startCol, startCol + blockSize - 1);
                Matrix dctBlock = T.times(block).times(Tt);

                double c1 = dctBlock.get(u1, v1);
                double c2 = dctBlock.get(u2, v2);
                int bit = (c1 > c2) ? 0 : 1;

                if (multiInsert) {
                    int pos = wmIdx % wmSize;
                    sum[pos] += bit;
                    cnt[pos]++;
                } else {
                    if (wmIdx < rawBits.length) {
                        rawBits[wmIdx] = bit;
                    }
                }

                wmIdx++;
                if (!multiInsert && wmIdx >= rawBits.length) break;
            }
            if (!multiInsert && wmIdx >= rawBits.length) break;
        }

        // Build output image.
        int[] finalBits;
        if (multiInsert) {
            finalBits = new int[wmSize];
            for (int i = 0; i < wmSize; i++) {
                finalBits[i] = (cnt[i] > 0 && sum[i] * 2 >= cnt[i]) ? 1 : 0;
            }
        } else {
            finalBits = rawBits;
        }

        BufferedImage result = new BufferedImage(wmWidth, wmHeight, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < wmHeight; y++) {
            for (int x = 0; x < wmWidth; x++) {
                int idx = y * wmWidth + x;
                int val = (idx < finalBits.length && finalBits[idx] == 1) ? 255 : 0;
                result.setRGB(x, y, (val << 16) | (val << 8) | val);
            }
        }
        return result;
    }
}
