package ru.krsk.buswatch;

import java.util.List;

/**
 * Freshness of the live feed.
 *
 * Bustime reports each vehicle's `ts` in the **local time of the city**, not
 * in UTC. Novosibirsk (UTC+7) therefore looks 7 hours ahead of UTC, while
 * Vladivostok (UTC+10) is 10 hours ahead — a naive "now - ts &gt; 300 s" check
 * throws away every vehicle of half the cities.
 *
 * The offset is unknown per request, so it is estimated from the data itself:
 * the newest timestamp of the feed is, by definition, the moment of the
 * snapshot. The offset between that and the device clock is the city's time
 * zone, and every other timestamp of the same feed is corrected with it.
 */
public final class FeedClock {

    /** A vehicle older than this (in device time) is considered stale. */
    public static final int MAX_AGE_SEC = 300;

    /** Plausible time zone offsets, hours. */
    private static final int MIN_OFFSET_H = -12;
    private static final int MAX_OFFSET_H = 14;

    /**
     * Upper bound for the estimated offset, in hours. A bigger gap means the
     * feed is not merely in another time zone but actually broken.
     */
    public static final int MAX_OFFSET_LIMIT_H = 14;

    private FeedClock() {
    }

    /**
     * Estimates the city's clock offset from the feed timestamps.
     *
     * @return seconds to add to a vehicle timestamp to obtain device time;
     * {@code Long.MAX_VALUE} when the feed is too far out of date to trust
     */
    public static long estimateOffset(Iterable<Vehicle> vehicles, long nowSec) {
        long newest = 0;
        for (Vehicle v : vehicles) {
            if (v.timestampSec > newest) newest = v.timestampSec;
        }
        if (newest <= 0) return 0;
        long diff = nowSec - newest;
        // The city may be in a different time zone: accept a shift of up to
        // 14 hours (UTC-12 … UTC+14). A larger gap means the feed is broken
        // and its vehicles must not be shown.
        if (diff > MAX_OFFSET_LIMIT_H * 3600L) return Long.MAX_VALUE;
        if (diff < MIN_OFFSET_H * 3600L) return Long.MIN_VALUE;
        return diff;
    }

    /**
     * True when the vehicle is fresh enough to be shown.
     *
     * @param offset correction from {@link #estimateOffset}
     */
    public static boolean isFresh(Vehicle v, long nowSec, long offset, int maxAgeSec) {
        if (offset == Long.MAX_VALUE || offset == Long.MIN_VALUE) return false;
        if (v.timestampSec <= 0) return true; // no timestamp: trust the feed
        long age = nowSec - (v.timestampSec + offset);
        // slightly tolerant: a vehicle may be reported a few seconds "in the future"
        return age <= maxAgeSec && age >= -120;
    }

    /** Convenience wrapper: estimates the offset and checks freshness. */
    public static boolean isFresh(Vehicle v, List<Vehicle> all, long nowSec, int maxAgeSec) {
        return isFresh(v, nowSec, estimateOffset(all, nowSec), maxAgeSec);
    }
}
