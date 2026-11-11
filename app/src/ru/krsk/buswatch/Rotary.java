package ru.krsk.buswatch;

/**
 * Turns the rotating crown / bezel motion into discrete list steps.
 * Wear-style rotary input reports AXIS_SCROLL in +/-1 increments, but some
 * HarmonyOS builds emit finer values, so deltas are accumulated.
 */
public final class Rotary {

    public static final float STEP = 0.75f;

    private float accumulator;

    /** Returns the number of list rows to move (negative = up). */
    public int onRotate(float delta) {
        if (Float.isNaN(delta) || delta == 0f) return 0;
        accumulator += delta;
        int steps = 0;
        while (accumulator >= STEP && steps < 3) {
            accumulator -= STEP;
            steps++;
        }
        while (accumulator <= -STEP && steps > -3) {
            accumulator += STEP;
            steps--;
        }
        // Safety: never let a stuck axis build up an unbounded accumulator.
        accumulator = Geo.clamp(accumulator, -STEP, STEP);
        return steps;
    }

    public void reset() {
        accumulator = 0f;
    }

    public float accumulator() {
        return accumulator;
    }
}
