package imageProcessing;

import java.awt.image.BufferedImage;

import Jama.Matrix;

final class ImagePreviewRenderer {

    private ImagePreviewRenderer() {
    }

    static BufferedImage renderRgbImage(ImageState.RgbChannels rgbChannels, int imageWidth, int imageHeight) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int rgb = (rgbChannels.getRed()[x][y] << 16)
                        | (rgbChannels.getGreen()[x][y] << 8)
                        | rgbChannels.getBlue()[x][y];
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    static BufferedImage renderRgbChannel(int[][] color, ProcessImage.ColorType type, boolean greyScale, int imageWidth, int imageHeight) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int value = color[x][y];
                int red = greyScale ? value : (type == ProcessImage.ColorType.RED ? value : 0);
                int green = greyScale ? value : (type == ProcessImage.ColorType.GREEN ? value : 0);
                int blue = greyScale ? value : (type == ProcessImage.ColorType.BLUE ? value : 0);
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return image;
    }

    static BufferedImage renderYCbCrChannel(Matrix color) {
        int width = color.getColumnDimension();
        int height = color.getRowDimension();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int value = clamp((int) Math.round(color.get(y, x)));
                image.setRGB(x, y, (value << 16) | (value << 8) | value);
            }
        }
        return image;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}