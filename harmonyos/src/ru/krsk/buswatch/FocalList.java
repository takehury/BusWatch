package ru.krsk.buswatch;

/**
 * "Fisheye" list geometry for the round watch: the row at the vertical centre
 * of the display is the largest, rows further from the centre shrink in BOTH
 * height and width, so the list follows the round screen on all four sides.
 * Rows also have a small gap between them so the list breathes.
 * Used by both the renderer and the touch hit-testing so they always agree.
 *
 * All values are in dp units (the renderer multiplies by density).
 */
public final class FocalList {

    public static final int VISIBLE = 5;

    /** Row slots (pitch) from top to bottom; the centre slot is tallest. */
    private static final float[] SLOT_H = {20f, 28f, 40f, 28f, 20f};

    /** Vertical scale factor per visible row (0 = top, 2 = centre, 4 = bottom). */
    private static final float[] SCALE = {0.72f, 0.86f, 1.00f, 0.86f, 0.72f};

    /** Horizontal scale factor — tapers harder than vertical to fit the circle. */
    private static final float[] W_SCALE = {0.74f, 0.90f, 1.00f, 0.90f, 0.74f};

    /** Visual gap between rows (dp). */
    public static final float GAP = 4f;

    private static final float TOTAL_H = 20f + 28f + 40f + 28f + 20f; // 136

    private FocalList() {
    }

    public static float totalHeightDp() {
        return TOTAL_H;
    }

    /** Visual height of a row (slot minus the gap). */
    public static float rowHeightDp(int visibleRow) {
        return SLOT_H[clampRow(visibleRow)] - GAP;
    }

    /** Top of the row's slot (pitch), in dp from the list top. */
    public static float rowTopDp(float listTopDp, int visibleRow) {
        float y = listTopDp;
        for (int i = 0; i < clampRow(visibleRow); i++) y += SLOT_H[i];
        return y;
    }

    public static float rowCenterDp(float listTopDp, int visibleRow) {
        return rowTopDp(listTopDp, visibleRow) + SLOT_H[clampRow(visibleRow)] / 2f;
    }

    /** Vertical scale for a visible row (0 = centre is largest). */
    public static float scale(int visibleRow) {
        return SCALE[clampRow(visibleRow)];
    }

    /** Width of a visible row in dp, tapering toward the round-screen edges. */
    public static float rowWidthDp(float maxWidthDp, int visibleRow) {
        return maxWidthDp * W_SCALE[clampRow(visibleRow)];
    }

    /**
     * Width of a row whose ends follow the round display: the row is as wide
     * as the circle chord at its farthest edge (minus a margin), so its left
     * and right sides describe the shape of the screen.
     */
    public static float widthForRow(float topDp, float heightDp, float radiusDp,
                                    float cxDp, float marginDp, float maxWidthDp) {
        float far = Math.max(Math.abs(topDp - cxDp), Math.abs(topDp + heightDp - cxDp));
        float half = (float) Math.sqrt(Math.max(0f, radiusDp * radiusDp - far * far));
        float w = 2f * (half - marginDp);
        return Math.max(0f, Math.min(maxWidthDp, w));
    }

    /** Scale from a distance-to-centre (0 = centre). */
    public static float scaleByDistance(int distanceFromCenter) {
        int d = Math.abs(distanceFromCenter);
        if (d <= 0) return 1.0f;
        if (d == 1) return 0.86f;
        return 0.72f;
    }

    /** Index of the row containing a dp coordinate, or -1 when outside the list. */
    public static int rowAt(float listTopDp, float yDp) {
        float y = listTopDp;
        for (int i = 0; i < VISIBLE; i++) {
            float h = SLOT_H[i];
            if (yDp >= y && yDp < y + h) return i;
            y += h;
        }
        return -1;
    }

    /**
     * First visible index. The list may start above the first item or run past
     * the last one, so EVERY item can be scrolled to the vertical centre:
     * empty slots are simply not drawn.
     */
    public static int topIndex(int size, int selected) {
        if (size <= 0) return 0;
        int half = VISIBLE / 2;
        if (size <= VISIBLE) return -(VISIBLE - size) / 2;
        int minTop = -half;
        int maxTop = size - 1 - half;
        return Geo.clamp(selected - half, minTop, maxTop);
    }

    /** Lowest possible first index (may be negative). */
    public static int minTop(int size) {
        if (size <= 0) return 0;
        return size <= VISIBLE ? -(VISIBLE - size) / 2 : -(VISIBLE / 2);
    }

    /** Highest possible first index (may exceed size - VISIBLE). */
    public static int maxTop(int size) {
        if (size <= 0) return 0;
        return size <= VISIBLE ? minTop(size) : size - 1 - VISIBLE / 2;
    }

    private static int clampRow(int row) {
        if (row < 0) return 0;
        if (row >= VISIBLE) return VISIBLE - 1;
        return row;
    }
}
