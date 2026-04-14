package watermark;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

// All 6 attacks from the assignment.
// Each takes a BufferedImage, returns a new attacked one.
public class WatermarkAttacks {

    // JPEG compression attack -- re-encode with given quality (0.0 - 1.0).
    public static BufferedImage jpegCompress(BufferedImage image, float quality) {
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.setOutput(new MemoryCacheImageOutputStream(baos));

            // JPEG doesn't support alpha, convert to RGB if needed.
            BufferedImage rgb = ensureRgb(image);
            writer.write(null, new IIOImage(rgb, null, null), param);
            writer.dispose();

            return ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException("JPEG compression attack failed", e);
        }
    }

    // PNG compression attack -- lossless re-encode then read back.
    public static BufferedImage pngCompress(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException("PNG compression attack failed", e);
        }
    }

    // Rotate image by given degrees (45 or 90 as per assignment).
    public static BufferedImage rotate(BufferedImage image, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int w = image.getWidth();
        int h = image.getHeight();

        // New bounding box after rotation.
        int newW = (int) Math.ceil(w * cos + h * sin);
        int newH = (int) Math.ceil(h * cos + w * sin);

        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();

        // White background for areas outside the rotated image.
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, newW, newH);

        AffineTransform transform = new AffineTransform();
        transform.translate((newW - w) / 2.0, (newH - h) / 2.0);
        transform.rotate(radians, w / 2.0, h / 2.0);
        g2d.setTransform(transform);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return result;
    }

    // Resize image to the given scale (0.75 for 75%, 0.5 for 50%).
    public static BufferedImage resize(BufferedImage image, double scale) {
        int newW = (int) Math.round(image.getWidth() * scale);
        int newH = (int) Math.round(image.getHeight() * scale);

        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(image, 0, 0, newW, newH, null);
        g2d.dispose();

        return result;
    }

    // Mirror (horizontal flip) the image.
    public static BufferedImage mirror(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(image, w, 0, -w, h, null);
        g2d.dispose();

        return result;
    }

    // Crop -- chop off a border of the given percentage from each edge.
    public static BufferedImage crop(BufferedImage image, double cropPercent) {
        int w = image.getWidth();
        int h = image.getHeight();

        int cropX = (int) (w * cropPercent);
        int cropY = (int) (h * cropPercent);

        int newW = w - 2 * cropX;
        int newH = h - 2 * cropY;

        if (newW <= 0 || newH <= 0) {
            throw new IllegalArgumentException("Crop percentage too large, nothing left.");
        }

        return image.getSubimage(cropX, cropY, newW, newH);
    }

    // Make sure image is TYPE_INT_RGB (required for JPEG).
    private static BufferedImage ensureRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
