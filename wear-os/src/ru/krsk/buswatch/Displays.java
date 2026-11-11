package ru.krsk.buswatch;

/**
 * Display adaptation. The whole UI is authored on a 466×466 canvas (Huawei
 * Watch 3 Pro, 320 dpi); any other watch simply scales that design, so a
 * 360×360, 396×396, 450×450 or 480×480 screen gets the same layout.
 */
public final class Displays {

    /** Design canvas size in pixels (466 px at 320 dpi ≈ 233 dp). */
    public static final float DESIGN = 466f;

    private Displays() {
    }

    /** Uniform scale for a square watch screen. */
    public static float scaleFor(int widthPx, int heightPx) {
        int min = Math.min(widthPx, heightPx);
        if (min <= 0) return 1f;
        return Geo.clamp(min / DESIGN, 0.6f, 1.25f);
    }

    /** True for screens close to square (watches are square or round). */
    public static boolean isSquareish(int widthPx, int heightPx) {
        int min = Math.min(widthPx, heightPx);
        int max = Math.max(widthPx, heightPx);
        return min > 0 && max / (float) min < 1.25f;
    }
}
