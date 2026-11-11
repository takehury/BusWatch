package ru.krsk.buswatch;

/**
 * Fisheye geometry for the route picker. Routes get a taller slot and a more
 * generous gap than the stop board, because the picker has more vertical room
 * and the extra air makes the list easier to hit with the crown/finger.
 */
public final class RouteList {

    public static final int VISIBLE = 5;

    /** Slot heights (pitch) top to bottom; the centre slot is tallest. */
    private static final float[] SLOT_H = {30f, 34f, 46f, 34f, 30f};

    /** Gap between route rows (dp). */
    public static final float GAP = 8f;

    private static final float TOTAL_H = 30f + 34f + 46f + 34f + 30f; // 174

    private RouteList() {
    }

    public static float totalHeightDp() {
        return TOTAL_H;
    }

    /** Visible height of a row (slot minus gap). */
    public static float rowHeightDp(int visibleRow) {
        return SLOT_H[clampRow(visibleRow)] - GAP;
    }

    public static float rowTopDp(float listTopDp, int visibleRow) {
        float y = listTopDp;
        for (int i = 0; i < clampRow(visibleRow); i++) y += SLOT_H[i];
        return y;
    }

    public static float rowCenterDp(float listTopDp, int visibleRow) {
        return rowTopDp(listTopDp, visibleRow) + SLOT_H[clampRow(visibleRow)] / 2f;
    }

    public static float scale(int visibleRow) {
        return FocalList.scale(visibleRow);
    }

    public static float rowWidthDp(float maxWidthDp, int visibleRow) {
        return FocalList.rowWidthDp(maxWidthDp, visibleRow);
    }

    public static int rowAt(float listTopDp, float yDp) {
        float y = listTopDp;
        for (int i = 0; i < VISIBLE; i++) {
            float h = SLOT_H[i];
            if (yDp >= y && yDp < y + h) return i;
            y += h;
        }
        return -1;
    }

    private static int clampRow(int row) {
        if (row < 0) return 0;
        if (row >= VISIBLE) return VISIBLE - 1;
        return row;
    }
}
