package watermark;

import Jama.Matrix;
import jpeg.Transform;
import enums.TransformType;

import java.awt.image.BufferedImage;

// DCT-domain watermarking: embeds a binary watermark by comparing/swapping
// two chosen frequency coefficients inside each DCT block.
// Based on the assignment description: coefficients (u1,v1) and (u2,v2)
// in mid-frequency range of each NxN block.
public class DctWatermark {

    // Embed watermark into a channel (Y recommended) using the DCT coefficient swap method.
    // channel: Matrix(height, width) — modified in-place.
    // watermark: B/W image to embed.
    // blockSize: transform block size (e.g. 8).
    // u1,v1,u2,v2: the two coefficient positions to compare/swap.
    // h: robustness depth — enforces |coeff1 - coeff2| > h.
    // multiInsert: tile watermark bits across all available blocks.
    public static void embed(Matrix channel, BufferedImage watermark, int blockSize,
                             int u1, int v1, int u2, int v2, double h, boolean multiInsert) {
        int rows = channel.getRowDimension();
        int cols = channel.getColumnDimension();

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

                // Forward DCT: T * block * T^T
                Matrix block = channel.getMatrix(startRow, startRow + blockSize - 1,
                        startCol, startCol + blockSize - 1);
                Matrix dctBlock = T.times(block).times(Tt);

                double c1 = dctBlock.get(u1, v1);
                double c2 = dctBlock.get(u2, v2);

                int bit = wmBits[bitIdx];

                // Bit == 0 → c1 > c2 must hold.
                // Bit == 1 → c1 <= c2 must hold.
                if (bit == 0) {
                    if (c1 <= c2) {
                        // Swap coefficients.
                        dctBlock.set(u1, v1, c2);
                        dctBlock.set(u2, v2, c1);
                        c1 = dctBlock.get(u1, v1);
                        c2 = dctBlock.get(u2, v2);
                    }
                    // Enforce robustness: |c1 - c2| > h.
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
                    if (h > 0 && Math.abs(c1 - c2) <= h) {
                        dctBlock.set(u1, v1, c1 - h / 2.0);
                        dctBlock.set(u2, v2, c2 + h / 2.0);
                    }
                }

                // Inverse DCT: T^T * dctBlock * T
                Matrix modified = Tt.times(dctBlock).times(T);
                channel.setMatrix(startRow, startRow + blockSize - 1,
                        startCol, startCol + blockSize - 1, modified);

                wmIdx++;
                if (!multiInsert && wmIdx >= wmBits.length) break;
            }
            if (!multiInsert && wmIdx >= wmBits.length) break;
        }
    }

    // Extract watermark from a watermarked channel.
    // Returns a B/W BufferedImage of the given watermark dimensions.
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
