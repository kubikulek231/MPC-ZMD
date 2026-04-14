package tests;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import Jama.Matrix;
import watermark.DctWatermark;

public class DctWatermarkTest {
    @Test
    void embedExtractImmediate() {
        // 64x64 channel = 8x8 blocks = 64 blocks, watermark 8x8 = 64 bits
        int size = 64;
        int blockSize = 8;
        int wmSize = 8;
        
        // Create a channel with some realistic pixel values
        double[][] data = new double[size][size];
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                data[r][c] = 128 + 40 * Math.sin(r * 0.1) * Math.cos(c * 0.1);
        Matrix channel = new Matrix(data);
        
        // Create a B/W watermark image
        BufferedImage wm = new BufferedImage(wmSize, wmSize, BufferedImage.TYPE_BYTE_GRAY);
        int[] expectedBits = new int[wmSize * wmSize];
        for (int y = 0; y < wmSize; y++) {
            for (int x = 0; x < wmSize; x++) {
                int bit = (x + y) % 2; // checkerboard
                expectedBits[y * wmSize + x] = bit;
                int val = bit == 1 ? 255 : 0;
                wm.setRGB(x, y, (val << 16) | (val << 8) | val);
            }
        }
        
        // Verify watermark was created correctly
        for (int y = 0; y < wmSize; y++) {
            for (int x = 0; x < wmSize; x++) {
                int gray = wm.getRGB(x, y) & 0xFF;
                int bit = gray > 128 ? 1 : 0;
                assertEquals(expectedBits[y * wmSize + x], bit, 
                    "Watermark pixel (" + x + "," + y + ") wrong before embed");
            }
        }
        
        // Embed with defaults: u1=3,v1=1, u2=4,v2=1, h=50, no multiInsert
        DctWatermark.embed(channel, wm, blockSize, 3, 1, 4, 1, 50.0, false);
        
        // Extract immediately from same channel
        BufferedImage extracted = DctWatermark.extract(channel, wmSize, wmSize, blockSize, 3, 1, 4, 1, false);
        
        int correct = 0;
        int total = wmSize * wmSize;
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < wmSize; y++) {
            for (int x = 0; x < wmSize; x++) {
                int gray = extracted.getRGB(x, y) & 0xFF;
                int extractedBit = gray > 128 ? 1 : 0;
                int expectedBit = expectedBits[y * wmSize + x];
                if (extractedBit == expectedBit) correct++;
                else sb.append("  MISMATCH at (" + x + "," + y + "): expected=" + expectedBit + " got=" + extractedBit + " gray=" + gray + "\n");
            }
        }
        System.out.println("Correct: " + correct + "/" + total);
        if (sb.length() > 0) System.out.println(sb);
        assertEquals(total, correct, "All watermark bits should match after immediate extract");
    }

    @Test
    void embedExtractWithOversizedWatermark() {
        // 64x64 channel with 8x8 blocks = 8x8 max watermark
        // But watermark is 100x100 -- should be auto-resized
        int channelSize = 64;
        int blockSize = 8;
        
        double[][] data = new double[channelSize][channelSize];
        for (int r = 0; r < channelSize; r++)
            for (int c = 0; c < channelSize; c++)
                data[r][c] = 128 + 40 * Math.sin(r * 0.1) * Math.cos(c * 0.1);
        Matrix channel = new Matrix(data);
        
        // Oversized watermark: 100x100 (way bigger than 8x8 blocks available)
        int wmOrigSize = 100;
        BufferedImage wm = new BufferedImage(wmOrigSize, wmOrigSize, BufferedImage.TYPE_BYTE_GRAY);
        // Draw a simple pattern: top half white, bottom half black
        for (int y = 0; y < wmOrigSize; y++)
            for (int x = 0; x < wmOrigSize; x++) {
                int val = (y < wmOrigSize / 2) ? 255 : 0;
                wm.setRGB(x, y, (val << 16) | (val << 8) | val);
            }
        
        // Max watermark size
        int[] maxWm = DctWatermark.maxWatermarkSize(channelSize, channelSize, blockSize);
        assertEquals(8, maxWm[0]);
        assertEquals(8, maxWm[1]);
        
        // Embed (auto-resizes internally)
        DctWatermark.embed(channel, wm, blockSize, 3, 1, 4, 1, 50.0, false);
        
        // Extract with capped dimensions (same as controller would do)
        int wmW = Math.min(wmOrigSize, maxWm[0]);
        int wmH = Math.min(wmOrigSize, maxWm[1]);
        BufferedImage extracted = DctWatermark.extract(channel, wmW, wmH, blockSize, 3, 1, 4, 1, false);
        
        assertEquals(8, extracted.getWidth());
        assertEquals(8, extracted.getHeight());
        
        // Top half should be mostly white (bit=1), bottom half mostly black (bit=0)
        int topWhite = 0, topTotal = 0, botBlack = 0, botTotal = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int gray = extracted.getRGB(x, y) & 0xFF;
                if (y < 4) { topTotal++; if (gray > 128) topWhite++; }
                else { botTotal++; if (gray <= 128) botBlack++; }
            }
        }
        System.out.println("Oversized test: top white=" + topWhite + "/" + topTotal + " bot black=" + botBlack + "/" + botTotal);
        assertTrue(topWhite >= topTotal * 0.7, "Top half should be mostly white");
        assertTrue(botBlack >= botTotal * 0.7, "Bottom half should be mostly black");
    }
}
