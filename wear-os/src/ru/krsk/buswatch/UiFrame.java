package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny, renderer-independent display list. WatchUi produces it, the Android
 * View replays it on a Canvas, and the host test harness replays it with
 * Java2D. That keeps the round-screen layout testable without an emulator.
 */
public final class UiFrame {

    public static final int RECT = 1;
    public static final int CIRCLE = 2;
    public static final int ROUND_RECT = 3;
    public static final int RING = 4;
    public static final int ARC = 5;
    public static final int LINE = 6;
    public static final int RADIAL = 7;
    public static final int TEXT = 8;
    public static final int CLIP_CIRCLE = 9;
    public static final int RESTORE = 10;

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    public final int width;
    public final int height;
    public final List<Op> ops = new ArrayList<>();

    public UiFrame(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void rect(float x, float y, float w, float h, int color) {
        Op op = new Op(RECT);
        op.x = x;
        op.y = y;
        op.w = w;
        op.h = h;
        op.color = color;
        ops.add(op);
    }

    public void circle(float cx, float cy, float r, int color) {
        Op op = new Op(CIRCLE);
        op.x = cx;
        op.y = cy;
        op.w = r;
        op.color = color;
        ops.add(op);
    }

    public void roundRect(float x, float y, float w, float h, float r, int color) {
        Op op = new Op(ROUND_RECT);
        op.x = x;
        op.y = y;
        op.w = w;
        op.h = h;
        op.r = r;
        op.color = color;
        ops.add(op);
    }

    public void ring(float cx, float cy, float r, float stroke, int color) {
        Op op = new Op(RING);
        op.x = cx;
        op.y = cy;
        op.w = r;
        op.stroke = stroke;
        op.color = color;
        ops.add(op);
    }

    public void arc(float cx, float cy, float r, float startDeg, float sweepDeg,
                    float stroke, int color) {
        Op op = new Op(ARC);
        op.x = cx;
        op.y = cy;
        op.w = r;
        op.start = startDeg;
        op.sweep = sweepDeg;
        op.stroke = stroke;
        op.color = color;
        ops.add(op);
    }

    public void line(float x1, float y1, float x2, float y2, float stroke, int color) {
        Op op = new Op(LINE);
        op.x = x1;
        op.y = y1;
        op.x2 = x2;
        op.y2 = y2;
        op.stroke = stroke;
        op.color = color;
        ops.add(op);
    }

    public void radial(float cx, float cy, float r, int inner, int outer) {
        Op op = new Op(RADIAL);
        op.x = cx;
        op.y = cy;
        op.w = r;
        op.color = inner;
        op.color2 = outer;
        ops.add(op);
    }

    public void text(float x, float baseline, float size, int color, int align,
                     boolean bold, String s) {
        Op op = new Op(TEXT);
        op.x = x;
        op.y = baseline;
        op.size = size;
        op.color = color;
        op.align = align;
        op.bold = bold;
        op.text = s;
        ops.add(op);
    }

    public void clipCircle(float cx, float cy, float r) {
        Op op = new Op(CLIP_CIRCLE);
        op.x = cx;
        op.y = cy;
        op.w = r;
        ops.add(op);
    }

    public void restore() {
        ops.add(new Op(RESTORE));
    }

    public static final class Op {
        public final int type;
        public float x, y, w, h, r;
        public float x2, y2;
        public float stroke = 1f;
        public float start, sweep;
        public int color;
        public int color2;
        public float size;
        public int align;
        public boolean bold;
        public String text = "";

        Op(int type) {
            this.type = type;
        }
    }
}
