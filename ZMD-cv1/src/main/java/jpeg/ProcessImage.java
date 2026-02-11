package jpeg;

import java.awt.Color;
import java.awt.image.BufferedImage;

import Jama.Matrix;
import graphics.Dialogs;

public class ProcessImage {
    public BufferedImage originalImage;
    public int imageHeight, imageWidth;
    
    public int[][] originalRed, modifiedRed;
    public int[][] originalGreen, modifiedGreen;
    public int[][] originalBlue, modifiedBlue;
    
    public Matrix originalY, modifiedY;
    public Matrix originalCb, modifiedCb;
    public Matrix originalCr, modifiedCr;
    
    public ProcessImage(BufferedImage image) {
        this.originalImage = image;
        if (image != null) {
            this.imageHeight = image.getHeight();
            this.imageWidth = image.getWidth();
            extractRGBMatrices();
        }
    }
    
    public void loadImage(String path) {
        this.originalImage = Dialogs.loadImageFromPath(path);
        if (originalImage != null) {
            this.imageHeight = originalImage.getHeight();
            this.imageWidth = originalImage.getWidth();
            extractRGBMatrices();
        }
    }
    
    private void extractRGBMatrices() {
        originalRed = new int[imageWidth][imageHeight];
        originalGreen = new int[imageWidth][imageHeight];
        originalBlue = new int[imageWidth][imageHeight];
        
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                Color color = new Color(originalImage.getRGB(x, y));
                originalRed[x][y] = color.getRed();
                originalGreen[x][y] = color.getGreen();
                originalBlue[x][y] = color.getBlue();
            }
        }
        
        // Initialize modified arrays as copies
        modifiedRed = copyMatrix(originalRed);
        modifiedGreen = copyMatrix(originalGreen);
        modifiedBlue = copyMatrix(originalBlue);
    }
    
    private int[][] copyMatrix(int[][] source) {
        int[][] copy = new int[source.length][source[0].length];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, source[i].length);
        }
        return copy;
    }
    
    public BufferedImage getImageFromRGB() {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int rgb = (modifiedRed[x][y] << 16) | 
                         (modifiedGreen[x][y] << 8) | 
                         modifiedBlue[x][y];
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
    
    public enum ColorType { RED, GREEN, BLUE }
    
    public BufferedImage showOneColorImageFromRGB(int[][] color, ColorType type, boolean greyScale) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int value = color[x][y];
                int r = greyScale ? value : (type == ColorType.RED ? value : 0);
                int g = greyScale ? value : (type == ColorType.GREEN ? value : 0);
                int b = greyScale ? value : (type == ColorType.BLUE ? value : 0);
                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
    
    public BufferedImage showOneColorImageFromYCbCr(Matrix color) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                double value = color.get(x, y);
                int clampedValue = (int) Math.round(value);
                clampedValue = Math.max(0, Math.min(255, clampedValue));
                int grey = (clampedValue << 16) | (clampedValue << 8) | clampedValue;
                image.setRGB(x, y, grey);
            }
        }
        return image;
    }
}