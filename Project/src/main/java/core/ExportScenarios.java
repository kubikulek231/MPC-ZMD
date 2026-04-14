package core;

// Predefined watermark scenarios and attacks for automated testing.
// Each method has 3 configs varying the key robustness parameter.
public class ExportScenarios {

    public record WmParams(String label, String method,
                           // LSB
                           String channel, int bitPlane, int key, int strength,
                           // DCT
                           int blockSize, int u1, int v1, int u2, int v2, double depth,
                           // Spread Spectrum
                           double alpha, int ssKey,
                           // Patchwork
                           double delta, int pwKey,
                           boolean multiInsert) {}

    // --- LSB: varying bit plane (0 = fragile, 4 = mid, 7 = MSB visible) ---
    public static final WmParams[] LSB = {
        new WmParams("bit=0 (LSB, fragile)",  "LSB", "Y", 0, 42, 0, 0,0,0,0,0, 0, 0,0, 0,0, false),
        new WmParams("bit=4 (mid-plane)",     "LSB", "Y", 4, 42, 0, 0,0,0,0,0, 0, 0,0, 0,0, false),
        new WmParams("bit=7 (MSB, visible)",  "LSB", "Y", 7, 42, 0, 0,0,0,0,0, 0, 0,0, 0,0, false),
    };

    // --- DCT: varying embedding depth (1 = weak, 10 = medium, 50 = strong) ---
    public static final WmParams[] DCT = {
        new WmParams("depth=1 (weak)",    "DCT", "Y", 0,0,0, 8,3,4,4,3, 1.0,  0,0, 0,0, false),
        new WmParams("depth=10 (medium)", "DCT", "Y", 0,0,0, 8,3,4,4,3, 10.0, 0,0, 0,0, false),
        new WmParams("depth=50 (strong)", "DCT", "Y", 0,0,0, 8,3,4,4,3, 50.0, 0,0, 0,0, false),
    };

    // --- Spread Spectrum: varying alpha (embedding strength) ---
    // Spatial-domain SS needs alpha >> pixel_std/sqrt(chips_per_bit) for reliable detection.
    // For Lenna (std~50, 64 chips/bit): SNR = alpha * 8 / 50, need SNR > 3 -> alpha > ~19.
    public static final WmParams[] SPREAD_SPECTRUM = {
        new WmParams("alpha=5 (weak)",    "Spread Spectrum", "Y", 0,0,0, 0,0,0,0,0, 0, 5.0,  42, 0,0, false),
        new WmParams("alpha=25 (medium)", "Spread Spectrum", "Y", 0,0,0, 0,0,0,0,0, 0, 25.0, 42, 0,0, false),
        new WmParams("alpha=50 (strong)", "Spread Spectrum", "Y", 0,0,0, 0,0,0,0,0, 0, 50.0, 42, 0,0, false),
    };

    // --- Patchwork: varying delta (1 = weak, 10 = medium, 50 = strong) ---
    public static final WmParams[] PATCHWORK = {
        new WmParams("delta=1 (weak)",    "Patchwork", "Y", 0,0,0, 0,0,0,0,0, 0, 0,0, 1.0,  42, false),
        new WmParams("delta=10 (medium)", "Patchwork", "Y", 0,0,0, 0,0,0,0,0, 0, 0,0, 10.0, 42, false),
        new WmParams("delta=50 (strong)", "Patchwork", "Y", 0,0,0, 0,0,0,0,0, 0, 0,0, 50.0, 42, false),
    };

    // All scenario groups: LSB uses large watermark, the rest use 64x64
    public static final WmParams[][] ALL_SCENARIOS = { LSB, DCT, SPREAD_SPECTRUM, PATCHWORK };
    public static final String[] SHEET_NAMES = { "LSB", "DCT", "Spread Spectrum", "Patchwork" };

    // Attacks to run for each scenario
    public static final String[] ATTACKS = {
        "None", "JPEG 10", "JPEG 30", "JPEG 50", "JPEG 70", "JPEG 90",
        "PNG", "Rotate 45", "Rotate 90", "Resize 75%", "Resize 50%", "Mirror", "Crop 10%"
    };
}
