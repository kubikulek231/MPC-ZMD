package core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import javax.imageio.ImageIO;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import Jama.Matrix;
import core.ExportScenarios.WmParams;
import imageProcessing.ProcessImage;
import watermark.DctWatermark;
import watermark.LsbWatermark;
import watermark.PatchworkWatermark;
import watermark.SpreadSpectrumWatermark;
import watermark.WatermarkAttacks;

// Runs watermark+attack combos and exports xlsx with embedded images.
public class ExportPipeline {

    // Callback for progress updates: current step, total steps, description.
    @FunctionalInterface
    public interface ProgressCallback {
        void update(int current, int total, String message);
    }

    private static final ProgressCallback NO_OP = (c, t, m) -> {};

    // Run ALL predefined scenarios (4 methods x 3 configs x 13 attacks).
    public static String runAll(BufferedImage originalImage,
                                BufferedImage lsbWatermark, BufferedImage smallWatermark,
                                File outputFile, ProgressCallback progress) throws Exception {

        BufferedImage[] watermarks = { lsbWatermark, smallWatermark, smallWatermark, smallWatermark };
        int totalRows = 0;
        for (var s : ExportScenarios.ALL_SCENARIOS) totalRows += s.length * ExportScenarios.ATTACKS.length;

        int[] done = { 0 };
        int total = totalRows;

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            for (int s = 0; s < ExportScenarios.ALL_SCENARIOS.length; s++) {
                Sheet sheet = wb.createSheet(ExportScenarios.SHEET_NAMES[s]);
                buildSheet(wb, sheet, originalImage, watermarks[s],
                           ExportScenarios.ALL_SCENARIOS[s], ExportScenarios.ATTACKS,
                           done, total, ExportScenarios.SHEET_NAMES[s], progress);
            }
            progress.update(total, total, "Writing file...");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                wb.write(fos);
            }
        }
        return outputFile.getAbsolutePath();
    }

    // Overload without progress callback (for tests).
    public static String runAll(BufferedImage originalImage,
                                BufferedImage lsbWatermark, BufferedImage smallWatermark,
                                File outputFile) throws Exception {
        return runAll(originalImage, lsbWatermark, smallWatermark, outputFile, NO_OP);
    }

    // Run a CUSTOM single-method export.
    public static String runCustom(BufferedImage originalImage, BufferedImage watermarkImage,
                                   WmParams params, String[] attacks,
                                   File outputFile, ProgressCallback progress) throws Exception {

        int total = attacks.length;
        int[] done = { 0 };

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(params.method());
            buildSheet(wb, sheet, originalImage, watermarkImage,
                       new WmParams[] { params }, attacks,
                       done, total, params.method(), progress);
            progress.update(total, total, "Writing file...");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                wb.write(fos);
            }
        }
        return outputFile.getAbsolutePath();
    }

    // Overload without progress callback (for tests).
    public static String runCustom(BufferedImage originalImage, BufferedImage watermarkImage,
                                   WmParams params, String[] attacks,
                                   File outputFile) throws Exception {
        return runCustom(originalImage, watermarkImage, params, attacks, outputFile, NO_OP);
    }

    private static void buildSheet(XSSFWorkbook wb, Sheet sheet,
                                   BufferedImage originalImage, BufferedImage watermarkImage,
                                   WmParams[] scenarios, String[] attacks,
                                   int[] done, int total, String sheetName,
                                   ProgressCallback progress) throws Exception {
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        // Header row
        Row header = sheet.createRow(0);
        String[] cols = { "Scenario", "Attack", "Parameters",
                          "Original", "Watermark", "Watermarked", "Extracted WM",
                          "Attacked", "Extracted After Attack" };
        CellStyle headerStyle = wb.createCellStyle();
        Font bold = wb.createFont();
        bold.setBold(true);
        headerStyle.setFont(bold);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        CellStyle wrapStyle = wb.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);
        for (int c = 0; c < cols.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(cols[c]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(c, c >= 3 ? 38 * 256 : 18 * 256);
        }

        int rowIdx = 1;
        for (WmParams params : scenarios) {
            int scenarioStartRow = rowIdx;

            for (String attack : attacks) {
                done[0]++;
                progress.update(done[0], total, sheetName + " | " + params.label() + " | " + attack);

                Row row = sheet.createRow(rowIdx);
                row.setHeightInPoints(200);

                row.createCell(0).setCellValue(params.label());
                row.createCell(1).setCellValue(attack);
                Cell paramsCell = row.createCell(2);
                paramsCell.setCellValue(formatParams(params));
                paramsCell.setCellStyle(wrapStyle);

                // --- Pipeline ---
                ProcessImage proc = new ProcessImage(originalImage);
                proc.convertToYCbCr();
                Matrix yChannel = proc.getWorkingYCbCr().getY();
                embedWatermark(params, yChannel, watermarkImage);
                proc.convertToRGB();
                BufferedImage watermarked = proc.getImageFromRGB();

                ProcessImage proc2 = new ProcessImage(watermarked);
                proc2.convertToYCbCr();
                Matrix yExtract = proc2.getWorkingYCbCr().getY();
                BufferedImage extractedClean = extractWatermark(params, yExtract, watermarkImage);

                embedImage(wb, drawing, sheet, rowIdx, 3, originalImage);
                embedImage(wb, drawing, sheet, rowIdx, 4, watermarkImage);
                embedImage(wb, drawing, sheet, rowIdx, 5, watermarked);
                embedImage(wb, drawing, sheet, rowIdx, 6, extractedClean);

                if (!"None".equals(attack)) {
                    BufferedImage attacked = applyAttack(attack, watermarked);
                    ProcessImage proc3 = new ProcessImage(attacked);
                    proc3.convertToYCbCr();
                    Matrix yAttacked = proc3.getWorkingYCbCr().getY();
                    BufferedImage extractedAttacked = extractWatermark(params, yAttacked, watermarkImage);

                    embedImage(wb, drawing, sheet, rowIdx, 7, attacked);
                    embedImage(wb, drawing, sheet, rowIdx, 8, extractedAttacked);
                }
                rowIdx++;
            }

            // Merge scenario label cells for this group
            if (rowIdx - scenarioStartRow > 1) {
                sheet.addMergedRegion(new CellRangeAddress(scenarioStartRow, rowIdx - 1, 0, 0));
            }
        }

        // Auto-size text columns to fit content
        for (int c = 0; c < 3; c++) sheet.autoSizeColumn(c);
    }

    private static void embedWatermark(WmParams p, Matrix yChannel, BufferedImage wm) {
        switch (p.method()) {
            case "LSB" -> LsbWatermark.embed(yChannel, wm, p.bitPlane(), p.key(), p.strength(), p.multiInsert());
            case "DCT" -> DctWatermark.embed(yChannel, wm, p.blockSize(), p.u1(), p.v1(), p.u2(), p.v2(), p.depth(), p.multiInsert());
            case "Spread Spectrum" -> SpreadSpectrumWatermark.embed(yChannel, wm, p.alpha(), p.ssKey(), p.multiInsert());
            case "Patchwork" -> PatchworkWatermark.embed(yChannel, wm, p.delta(), p.pwKey(), p.multiInsert());
        }
    }

    private static BufferedImage extractWatermark(WmParams p, Matrix yChannel, BufferedImage wm) {
        int wmW = wm.getWidth(), wmH = wm.getHeight();
        return switch (p.method()) {
            case "LSB" -> LsbWatermark.extract(yChannel, wmW, wmH, p.bitPlane(), p.key(), p.multiInsert());
            case "DCT" -> {
                int[] max = DctWatermark.maxWatermarkSize(yChannel.getRowDimension(), yChannel.getColumnDimension(), p.blockSize());
                yield DctWatermark.extract(yChannel, Math.min(wmW, max[0]), Math.min(wmH, max[1]),
                        p.blockSize(), p.u1(), p.v1(), p.u2(), p.v2(), p.multiInsert());
            }
            case "Spread Spectrum" -> {
                int[] max = SpreadSpectrumWatermark.maxWatermarkSize(yChannel.getRowDimension(), yChannel.getColumnDimension());
                yield SpreadSpectrumWatermark.extract(yChannel, Math.min(wmW, max[0]), Math.min(wmH, max[1]),
                        p.alpha(), p.ssKey(), p.multiInsert());
            }
            case "Patchwork" -> {
                int[] max = PatchworkWatermark.maxWatermarkSize(yChannel.getRowDimension(), yChannel.getColumnDimension());
                yield PatchworkWatermark.extract(yChannel, Math.min(wmW, max[0]), Math.min(wmH, max[1]),
                        p.delta(), p.pwKey(), p.multiInsert());
            }
            default -> new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        };
    }

    private static BufferedImage applyAttack(String attack, BufferedImage image) {
        return switch (attack) {
            case "JPEG 10" -> WatermarkAttacks.jpegCompress(image, 0.10f);
            case "JPEG 30" -> WatermarkAttacks.jpegCompress(image, 0.30f);
            case "JPEG 50" -> WatermarkAttacks.jpegCompress(image, 0.50f);
            case "JPEG 70" -> WatermarkAttacks.jpegCompress(image, 0.70f);
            case "JPEG 90" -> WatermarkAttacks.jpegCompress(image, 0.90f);
            case "PNG" -> WatermarkAttacks.pngCompress(image);
            case "Rotate 45" -> WatermarkAttacks.rotate(image, 45);
            case "Rotate 90" -> WatermarkAttacks.rotate(image, 90);
            case "Resize 75%" -> WatermarkAttacks.resize(image, 0.75);
            case "Resize 50%" -> WatermarkAttacks.resize(image, 0.50);
            case "Mirror" -> WatermarkAttacks.mirror(image);
            case "Crop 10%" -> WatermarkAttacks.crop(image, 0.10);
            default -> image;
        };
    }

    private static String formatParams(WmParams p) {
        return switch (p.method()) {
            case "LSB" -> "Channel: " + p.channel() + "\nBit plane: " + p.bitPlane()
                    + "\nKey: " + p.key() + "\nStrength: " + p.strength()
                    + "\nMulti-insert: " + p.multiInsert();
            case "DCT" -> "Block size: " + p.blockSize()
                    + "\nCoeff 1: (" + p.u1() + "," + p.v1() + ")"
                    + "\nCoeff 2: (" + p.u2() + "," + p.v2() + ")"
                    + "\nDepth: " + p.depth()
                    + "\nMulti-insert: " + p.multiInsert();
            case "Spread Spectrum" -> "Alpha: " + p.alpha()
                    + "\nKey: " + p.ssKey()
                    + "\nMulti-insert: " + p.multiInsert();
            case "Patchwork" -> "Delta: " + p.delta()
                    + "\nKey: " + p.pwKey()
                    + "\nMulti-insert: " + p.multiInsert();
            default -> "";
        };
    }

    private static BufferedImage scaleDown(BufferedImage src, int maxDim) {
        // Kept for potential future use but currently unused - we embed full-res.
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = (int)(w * scale), nh = (int)(h * scale);
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private static void embedImage(XSSFWorkbook wb, Drawing<?> drawing, Sheet sheet,
                                   int row, int col, BufferedImage img) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] bytes = baos.toByteArray();

        int pictureIdx = wb.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
        ClientAnchor anchor = wb.getCreationHelper().createClientAnchor();
        anchor.setCol1(col);
        anchor.setRow1(row);
        anchor.setCol2(col + 1);
        anchor.setRow2(row + 1);
        drawing.createPicture(anchor, pictureIdx);
    }
}
