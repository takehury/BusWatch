package ru.krsk.buswatch;

/**
 * Momentum after a swipe: the list keeps moving and slows down exponentially.
 * Works in fractional rows so the animation is smooth, and is pure Java so the
 * feel can be unit-tested.
 */
public final class Inertia {

    /** Deceleration, 1/s: velocity drops to 37% every 1/FRICTION seconds. */
    public static final float FRICTION = 6.5f;

    /** Below this speed (rows/s) the movement stops. */
    public static final float MIN_SPEED = 1.2f;

    /** Safety cap so a wild flick cannot spin the list forever. */
    public static final int MAX_ROWS = 10;

    private float speed;      // signed rows per second
    private float position;   // fractional rows not applied yet
    private int moved;

    public Inertia(float rowsPerSecond) {
        this.speed = rowsPerSecond;
    }

    /**
     * Converts a vertical fling speed (design px/s, negative = finger up) into
     * rows per second. Swiping up must move the list forward, hence the sign
     * flip — this is the direction users expect.
     */
    public static float rowsPerSecond(float velocityYPx, float rowPx) {
        if (rowPx <= 0) return 0f;
        return -velocityYPx / rowPx;
    }

    public boolean active() {
        return moved < MAX_ROWS && (Math.abs(speed) >= MIN_SPEED || Math.abs(position) >= 1f);
    }

    /**
     * Advances the animation.
     *
     * @param dt seconds since the previous frame
     * @return signed whole rows to scroll now (may be 0)
     */
    public int update(float dt) {
        if (dt <= 0) return 0;
        if (dt > 0.05f) dt = 0.05f; // clamp after a lag spike
        position += speed * dt;
        speed *= (float) Math.exp(-FRICTION * dt);

        int whole = (int) position;
        position -= whole;
        moved += Math.abs(whole);
        return whole;
    }

    public float speed() {
        return speed;
    }
}
