package tests;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import Jama.Matrix;
import core.ExportPipeline;
import core.ExportScenarios;
import imageProcessing.ProcessImage;
import watermark.SpreadSpectrumWatermark;

public class ExportPipelineTest {

    private static final String LENNA_PATH = "Images/Lenna.png";
    private static final String WATERMARK_LARGE_PATH = "Images/watermark.png";
    private static final String WATERMARK_SMALL_PATH = "Images/watermark_64.png";

    @Test
    void exportAllScenarios() throws Exception {
        BufferedImage original = ImageIO.read(new File(LENNA_PATH));
        BufferedImage wmLarge = ImageIO.read(new File(WATERMARK_LARGE_PATH));
        BufferedImage wmSmall = ImageIO.read(new File(WATERMARK_SMALL_PATH));

        File output = new File("target/watermark-report-all.xlsx");

        String path = ExportPipeline.runAll(original, wmLarge, wmSmall, output);

        assertTrue(output.exists(), "XLSX file should exist");
        assertTrue(output.length() > 10_000, "XLSX file should not be empty");
        System.out.println("Full report saved to: " + path);
        System.out.println("File size: " + (output.length() / 1024) + " KB");
    }

    @Test
    void exportCustomSingleAttack() throws Exception {
        BufferedImage original = ImageIO.read(new File(LENNA_PATH));
        BufferedImage wmSmall = ImageIO.read(new File(WATERMARK_SMALL_PATH));

        // Custom DCT scenario with a single JPEG 50 attack
        ExportScenarios.WmParams dct = new ExportScenarios.WmParams(
                "Custom DCT", "DCT", "Y", 0, 0, 0,
                8, 3, 4, 4, 3, 10.0, 0, 0, 0, 0, false);

        File output = new File("target/watermark-report-custom.xlsx");

        String path = ExportPipeline.runCustom(original, wmSmall, dct,
                new String[] { "None", "JPEG 50" }, output);

        assertTrue(output.exists(), "XLSX file should exist");
        assertTrue(output.length() > 1_000, "XLSX file should not be empty");
        System.out.println("Custom report saved to: " + path);
        System.out.println("File size: " + (output.length() / 1024) + " KB");
    }

    @Test
    void spreadSpectrumEmbedExtract() throws Exception {
        BufferedImage original = ImageIO.read(new File(LENNA_PATH));
        BufferedImage wm = ImageIO.read(new File(WATERMARK_SMALL_PATH));
        int wmW = wm.getWidth(), wmH = wm.getHeight();

        // Convert to YCbCr, embed, extract immediately
        ProcessImage proc = new ProcessImage(original);
        proc.convertToYCbCr();
        Matrix y = proc.getWorkingYCbCr().getY();

        double alpha = 25.0;
        int key = 42;
        SpreadSpectrumWatermark.embed(y, wm, alpha, key, false);

        BufferedImage extracted = SpreadSpectrumWatermark.extract(y, wmW, wmH, alpha, key, false);

        // Count matching bits between original watermark and extracted
        int total = wmW * wmH;
        int correct = 0;
        for (int py = 0; py < wmH; py++) {
            for (int px = 0; px < wmW; px++) {
                int origBit = ((wm.getRGB(px, py) & 0xFF) > 128) ? 1 : 0;
                int extBit = ((extracted.getRGB(px, py) & 0xFF) > 128) ? 1 : 0;
                if (origBit == extBit) correct++;
            }
        }

        double accuracy = (double) correct / total * 100;
        System.out.println("SS embed/extract accuracy: " + String.format("%.1f", accuracy) + "% (" + correct + "/" + total + ")");
        assertTrue(accuracy > 70, "Spread Spectrum should have >70% accuracy with alpha=25, got " + accuracy + "%");
    }
}
