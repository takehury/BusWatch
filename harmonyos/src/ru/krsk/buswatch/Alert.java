package ru.krsk.buswatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Stateful detector for "the bus reached the marked stop".
 *
 * It tracks each vehicle by id, so it fires only when:
 *  - a vehicle is observed exactly at the marked stop, or
 *  - a vehicle we previously saw *before* the stop jumps past it between polls.
 *
 * A bus that was already past the stop when the alert was armed never fires,
 * and one bus passing the stop does not trigger an alert meant for another.
 */
public final class Alert {

    private final int stopIndex;
    private final Map<String, Integer> previous = new HashMap<>();
    private boolean fired;

    public Alert(int stopIndex) {
        this.stopIndex = stopIndex;
    }

    /** True when a watched bus reached the marked stop (fires exactly once). */
    public boolean check(Map<String, Integer> orders) {
        if (fired) return true;
        boolean hit = false;
        for (Map.Entry<String, Integer> e : orders.entrySet()) {
            int now = e.getValue() == null ? -1 : e.getValue();
            if (now == stopIndex) {
                hit = true;
                break;
            }
            Integer was = previous.get(e.getKey());
            if (was != null && was >= 0 && was < stopIndex && now > stopIndex) {
                hit = true;
                break;
            }
        }
        previous.clear();
        previous.putAll(orders);
        if (hit) fired = true;
        return fired;
    }

    public boolean isFired() {
        return fired;
    }

    public int stopIndex() {
        return stopIndex;
    }

    public void reset() {
        previous.clear();
        fired = false;
    }
}
