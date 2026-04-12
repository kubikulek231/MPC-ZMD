package imageProcessing;

import Jama.Matrix;
import jpeg.Sampling;
import enums.SamplingType;

final class ImageColorSpaceConverter {

    private ImageColorSpaceConverter() {
    }

    static void convertRgbToYCbCr(ImageState originalState, ImageState workingState, int imageWidth, int imageHeight) {
        Matrix originalY = new Matrix(imageHeight, imageWidth);
        Matrix originalCb = new Matrix(imageHeight, imageWidth);
        Matrix originalCr = new Matrix(imageHeight, imageWidth);

        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                double red = originalState.getRgb().getRed()[x][y];
                double green = originalState.getRgb().getGreen()[x][y];
                double blue = originalState.getRgb().getBlue()[x][y];

                // Usual constants for converting from RGB to YCbCr (BT.601); Cb/Cr are shifted by +128.
                originalY.set(y, x, 0.299 * red + 0.587 * green + 0.114 * blue);
                originalCb.set(y, x, -0.169 * red - 0.331 * green + 0.500 * blue + 128.0);
                originalCr.set(y, x, 0.500 * red - 0.419 * green - 0.081 * blue + 128.0);
            }
        }

        originalState.getYCbCr().setY(originalY);
        originalState.getYCbCr().setCb(originalCb);
        originalState.getYCbCr().setCr(originalCr);
        workingState.copyYCbCrFrom(originalState);
    }

    static void convertYCbCrToRgb(ImageState workingState, int imageWidth, int imageHeight, SamplingType lastSamplingType) {
        Matrix modifiedY = workingState.getYCbCr().getY();
        Matrix modifiedCb = workingState.getYCbCr().getCb();
        Matrix modifiedCr = workingState.getYCbCr().getCr();

        Matrix cbFull = (modifiedCb.getRowDimension() != imageHeight || modifiedCb.getColumnDimension() != imageWidth)
                ? Sampling.sampleUp(modifiedCb, lastSamplingType)
                : modifiedCb;
        Matrix crFull = (modifiedCr.getRowDimension() != imageHeight || modifiedCr.getColumnDimension() != imageWidth)
                ? Sampling.sampleUp(modifiedCr, lastSamplingType)
                : modifiedCr;

        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                double yChannel = modifiedY.get(y, x);
                // Remove +128 chroma offset before inverse conversion back to RGB.
                double cbChannel = cbFull.get(y, x) - 128.0;
                double crChannel = crFull.get(y, x) - 128.0;

                // Inverse BT.601 YCbCr -> RGB formulas.
                workingState.getRgb().getRed()[x][y] = clamp((int) Math.round(yChannel + 1.402 * crChannel));
                workingState.getRgb().getGreen()[x][y] = clamp((int) Math.round(yChannel - 0.344 * cbChannel - 0.714 * crChannel));
                workingState.getRgb().getBlue()[x][y] = clamp((int) Math.round(yChannel + 1.772 * cbChannel));
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}