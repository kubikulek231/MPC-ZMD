package imageProcessing;

import Jama.Matrix;
import enums.QualityType;
import jpeg.Quality;

final class ImageMetricsCalculator {

    private ImageMetricsCalculator() {
    }

    static double[] calculateMetrics(ImageState originalState, ImageState workingState, QualityType type) {
        switch (type) {
            case RED:
                return metricsForChannels(Quality.convertIntToDouble(originalState.getRgb().getRed()), Quality.convertIntToDouble(workingState.getRgb().getRed()));
            case GREEN:
                return metricsForChannels(Quality.convertIntToDouble(originalState.getRgb().getGreen()), Quality.convertIntToDouble(workingState.getRgb().getGreen()));
            case BLUE:
                return metricsForChannels(Quality.convertIntToDouble(originalState.getRgb().getBlue()), Quality.convertIntToDouble(workingState.getRgb().getBlue()));
            case Y:
                return metricsForChannels(originalState.getYCbCr().getY().getArray(), workingState.getYCbCr().getY().getArray());
            case CB:
                return metricsForMatchedChannels(originalState.getYCbCr().getCb(), workingState.getYCbCr().getCb(), "Cb");
            case CR:
                return metricsForMatchedChannels(originalState.getYCbCr().getCr(), workingState.getYCbCr().getCr(), "Cr");
            case RGB: {
                double[] red = metricsForChannels(Quality.convertIntToDouble(originalState.getRgb().getRed()), Quality.convertIntToDouble(workingState.getRgb().getRed()));
                double[] green = metricsForChannels(Quality.convertIntToDouble(originalState.getRgb().getGreen()), Quality.convertIntToDouble(workingState.getRgb().getGreen()));
                double[] blue = metricsForChannels(Quality.convertIntToDouble(originalState.getRgb().getBlue()), Quality.convertIntToDouble(workingState.getRgb().getBlue()));
                return averageMetrics(red, green, blue);
            }
            case YCBCR: {
                double[] y = metricsForChannels(originalState.getYCbCr().getY().getArray(), workingState.getYCbCr().getY().getArray());
                double[] cb = metricsForMatchedChannels(originalState.getYCbCr().getCb(), workingState.getYCbCr().getCb(), "Cb");
                double[] cr = metricsForMatchedChannels(originalState.getYCbCr().getCr(), workingState.getYCbCr().getCr(), "Cr");
                return averageMetrics(y, cb, cr);
            }
            default:
                throw new IllegalArgumentException("Unknown QualityType: " + type);
        }
    }

    static double[] calculateSSIMMetrics(ImageState originalState, ImageState workingState, QualityType type) {
        switch (type) {
            case Y:
                return ssimForChannels(originalState.getYCbCr().getY().getArray(), workingState.getYCbCr().getY().getArray());
            case CB:
                return ssimForMatchedChannels(originalState.getYCbCr().getCb(), workingState.getYCbCr().getCb(), "Cb");
            case CR:
                return ssimForMatchedChannels(originalState.getYCbCr().getCr(), workingState.getYCbCr().getCr(), "Cr");
            default:
                throw new IllegalArgumentException("SSIM is only supported for Y, Cb and Cr channels.");
        }
    }

    static double calculateRgbMse(ImageState originalState, ImageState workingState, int imageWidth, int imageHeight) {
        long sum = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int dr = originalState.getRgb().getRed()[x][y] - workingState.getRgb().getRed()[x][y];
                int dg = originalState.getRgb().getGreen()[x][y] - workingState.getRgb().getGreen()[x][y];
                int db = originalState.getRgb().getBlue()[x][y] - workingState.getRgb().getBlue()[x][y];
                // Per-pixel RGB squared error: (dR^2 + dG^2 + dB^2).
                sum += dr * dr + dg * dg + db * db;
            }
        }
        // Final MSE = average over all color components of all pixels.
        return (double) sum / (3L * imageWidth * imageHeight);
    }

    static double calculateRgbPsnr(ImageState originalState, ImageState workingState, int imageWidth, int imageHeight) {
        double mse = calculateRgbMse(originalState, workingState, imageWidth, imageHeight);
        if (mse == 0) {
            return Double.POSITIVE_INFINITY;
        }
        // PSNR formula in dB, assuming 8-bit channels (MAX_I = 255).
        return 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    private static double[] averageMetrics(double[] first, double[] second, double[] third) {
        // RGB and YCbCr aggregate metrics are simple channel averages.
        double mse = (first[0] + second[0] + third[0]) / 3.0;
        double mae = (first[1] + second[1] + third[1]) / 3.0;
        double sae = (first[2] + second[2] + third[2]) / 3.0;
        return new double[]{mse, mae, sae, Quality.countPSNR(mse)};
    }

    private static double[] metricsForChannels(double[][] original, double[][] modified) {
        double mse = Quality.countMSE(original, modified);
        return new double[]{mse, Quality.countMAE(original, modified), Quality.countSAE(original, modified), Quality.countPSNR(mse)};
    }

    private static double[] metricsForMatchedChannels(Matrix original, Matrix modified, String name) {
        if (original.getRowDimension() != modified.getRowDimension()
                || original.getColumnDimension() != modified.getColumnDimension()) {
            throw new IllegalStateException(
                    name + " channel dimensions don't match ("
                            + original.getRowDimension() + "x" + original.getColumnDimension() + " vs "
                            + modified.getRowDimension() + "x" + modified.getColumnDimension()
                            + "). Apply inverse subsampling first.");
        }
        return metricsForChannels(original.getArray(), modified.getArray());
    }

    private static double[] ssimForChannels(double[][] original, double[][] modified) {
        return new double[]{Quality.countSSIM(new Matrix(original), new Matrix(modified)),
                Quality.countMSSIM(new Matrix(original), new Matrix(modified))};
    }

    private static double[] ssimForMatchedChannels(Matrix original, Matrix modified, String name) {
        if (original.getRowDimension() != modified.getRowDimension()
                || original.getColumnDimension() != modified.getColumnDimension()) {
            throw new IllegalStateException(name + " channel dimensions don't match. Apply inverse subsampling first.");
        }
        return ssimForChannels(original.getArray(), modified.getArray());
    }
}