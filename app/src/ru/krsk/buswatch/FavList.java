package ru.krsk.buswatch;

/**
 * Geometry of the favourites list: same fisheye feel as the route picker, but
 * four rows so the whole list (with the header buttons above) stays inside the
 * circle. The chosen row is always the biggest.
 */
public final class FavList {

    public static final int VISIBLE = 4;

    /**
     * Slot heights top to bottom. The selected row is pinned to slot 1, whose
     * centre lands exactly on the display centre (116.5 dp) with the list top
     * at {@link #TOP} — see {@link #SELECTED_SLOT}.
     */
    private static final float[] SLOT_H = {30f, 47f, 34f, 26f};

    /** Slot that holds the selected row (its centre is the display centre). */
    public static final int SELECTED_SLOT = 1;

    /** List top: starts below the header buttons and centres slot 1. */
    public static final float TOP = 63f;

    /** Gap between rows (dp); a little tighter than on the main route list. */
    public static final float GAP = 5f;

    private static final float TOTAL_H = 30f + 47f + 34f + 26f; // 137

    private FavList() {
    }

    public static float totalHeightDp() {
        return TOTAL_H;
    }

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

    /** Scale relative to the selection: the chosen row is always the biggest. */
    public static float scaleFor(int visibleRow, int selectedRow) {
        if (visibleRow == selectedRow) return 1f;
        int d = Math.abs(visibleRow - selectedRow);
        return d == 1 ? 0.88f : 0.76f;
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
