package imageProcessing;

import Jama.Matrix;

public final class ImageState {

    private final RgbChannels rgb;
    private final YCbCrChannels yCbCr;

    public ImageState(int width, int height) {
        this.rgb = new RgbChannels(width, height);
        this.yCbCr = new YCbCrChannels(height, width);
    }

    public RgbChannels getRgb() {
        return rgb;
    }

    public YCbCrChannels getYCbCr() {
        return yCbCr;
    }

    public void reset(int width, int height) {
        rgb.reset(width, height);
        yCbCr.reset(height, width);
    }

    public void copyYCbCrFrom(ImageState source) {
        yCbCr.setY(source.getYCbCr().getY().copy());
        yCbCr.setCb(source.getYCbCr().getCb().copy());
        yCbCr.setCr(source.getYCbCr().getCr().copy());
    }

    public static final class RgbChannels {
        private int[][] red;
        private int[][] green;
        private int[][] blue;

        public RgbChannels(int width, int height) {
            red = new int[width][height];
            green = new int[width][height];
            blue = new int[width][height];
        }

        public int[][] getRed() {
            return red;
        }

        public void setRed(int[][] red) {
            this.red = red;
        }

        public int[][] getGreen() {
            return green;
        }

        public void setGreen(int[][] green) {
            this.green = green;
        }

        public int[][] getBlue() {
            return blue;
        }

        public void setBlue(int[][] blue) {
            this.blue = blue;
        }

        public void reset(int width, int height) {
            red = new int[width][height];
            green = new int[width][height];
            blue = new int[width][height];
        }
    }

    public static final class YCbCrChannels {
        private Matrix y;
        private Matrix cb;
        private Matrix cr;

        public YCbCrChannels(int height, int width) {
            y = new Matrix(height, width);
            cb = new Matrix(height, width);
            cr = new Matrix(height, width);
        }

        public Matrix getY() {
            return y;
        }

        public void setY(Matrix y) {
            this.y = y;
        }

        public Matrix getCb() {
            return cb;
        }

        public void setCb(Matrix cb) {
            this.cb = cb;
        }

        public Matrix getCr() {
            return cr;
        }

        public void setCr(Matrix cr) {
            this.cr = cr;
        }

        public void reset(int height, int width) {
            y = new Matrix(height, width);
            cb = new Matrix(height, width);
            cr = new Matrix(height, width);
        }
    }
}