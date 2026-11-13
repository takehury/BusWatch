package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a per-stop "arrival board" for one route and one direction:
 * which buses are at each stop now, and the estimated wait for the next bus.
 */
public final class Board {

    /** Rough average time between two consecutive stops, seconds. */
    public static final int SECONDS_PER_STOP = 100;

    public final List<Row> rows;
    public final int userStopIndex;

    private Board(List<Row> rows, int userStopIndex) {
        this.rows = rows;
        this.userStopIndex = userStopIndex;
    }

    public static final class Row {
        public final int index;
        public final StopInfo stop;
        public final List<Vehicle> atStop;   // buses currently at this stop
        public final int etaMinutes;         // 0 = bus here now, -1 = no forecast
        public final Vehicle nextBus;        // closest bus behind this stop
        public final boolean isUserStop;

        Row(int index, StopInfo stop, List<Vehicle> atStop, int etaMinutes,
            Vehicle nextBus, boolean isUserStop) {
            this.index = index;
            this.stop = stop;
            this.atStop = Collections.unmodifiableList(atStop);
            this.etaMinutes = etaMinutes;
            this.nextBus = nextBus;
            this.isUserStop = isUserStop;
        }

        public boolean hasBusHere() {
            return !atStop.isEmpty();
        }
    }

    public static Board build(List<StopInfo> stops, List<Vehicle> vehicles, int dir,
                              double userLat, double userLon, long nowSec, int maxAgeSec) {
        List<Row> rows = new ArrayList<>();
        // bucket live vehicles by their stop index in this direction
        Map<Integer, List<Vehicle>> byOrder = new HashMap<>();
        List<Vehicle> live = new ArrayList<>();
        List<Vehicle> all = unique(vehicles);
        // the feed uses the city's local time; correct it before judging age
        long offset = FeedClock.estimateOffset(all, nowSec);
        for (Vehicle v : all) {
            if (v.dir != dir) continue;
            if (v.sleeping) continue;
            if (!FeedClock.isFresh(v, nowSec, offset, maxAgeSec)) continue;
            live.add(v);
            int o = resolveOrder(v, stops);
            if (o < 0) continue;
            List<Vehicle> list = byOrder.get(o);
            if (list == null) {
                list = new ArrayList<>();
                byOrder.put(o, list);
            }
            list.add(v);
        }

        // nearest stop to the user (within this direction's list)
        int userIdx = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < stops.size(); i++) {
            double d = Geo.distanceMeters(userLat, userLon, stops.get(i).lat, stops.get(i).lon);
            if (d < best) {
                best = d;
                userIdx = i;
            }
        }

        // for each stop, find the closest bus behind it (largest order < i)
        for (int i = 0; i < stops.size(); i++) {
            List<Vehicle> at = byOrder.get(i);
            if (at == null) at = Collections.emptyList();
            int eta = -1;
            Vehicle next = null;
            if (!at.isEmpty()) {
                eta = 0; // a bus is here now
            } else {
                int bestOrder = -1;
                for (Vehicle v : live) {
                    int o = resolveOrder(v, stops);
                    if (o < i && o > bestOrder) {
                        bestOrder = o;
                        next = v;
                    }
                }
                if (bestOrder >= 0) {
                    int stopsAhead = i - bestOrder;
                    eta = Math.max(1, (int) Math.round(stopsAhead * SECONDS_PER_STOP / 60.0));
                }
            }
            rows.add(new Row(i, stops.get(i), at, eta, next, i == userIdx));
        }
        return new Board(rows, userIdx);
    }

    /**
     * Drops duplicate vehicles (same uid). The feed and the rebuilt board can
     * repeat a bus several times, which used to draw several "here" dots for a
     * single vehicle.
     */
    public static List<Vehicle> unique(List<Vehicle> in) {
        Map<String, Vehicle> map = new LinkedHashMap<>();
        for (Vehicle v : in) {
            String key = v.uid == null || v.uid.isEmpty() ? v.key() : v.uid;
            if (!map.containsKey(key)) map.put(key, v);
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Resolves a vehicle's stop index: prefers the feed's `order` field, then
     * falls back to matching stopId against the ordered list.
     */
    private static int resolveOrder(Vehicle v, List<StopInfo> stops) {
        if (v.order >= 0 && v.order < stops.size()) return v.order;
        if (v.stopId > 0) {
            for (int i = 0; i < stops.size(); i++) {
                if (stops.get(i).id == v.stopId) return i;
            }
        }
        return -1;
    }

    /**
     * Resolved stop indices of the live buses in one direction, keyed by
     * vehicle id. Used by the alert detector and the background service.
     */
    public static Map<String, Integer> resolveVehicleOrders(List<StopInfo> stops,
                                                            List<Vehicle> vehicles,
                                                            int dir, long nowSec, int maxAgeSec) {
        Map<String, Integer> out = new HashMap<>();
        long offset = FeedClock.estimateOffset(vehicles, nowSec);
        for (Vehicle v : vehicles) {
            if (v.dir != dir) continue;
            if (v.sleeping) continue;
            if (!FeedClock.isFresh(v, nowSec, offset, maxAgeSec)) continue;
            int o = resolveOrder(v, stops);
            if (o >= 0) out.put(v.uid.isEmpty() ? v.key() : v.uid, o);
        }
        return out;
    }

    /** Sorts a list of vehicles at the same stop by plate for stable display. */
    public static List<Vehicle> sortedByPlate(List<Vehicle> in) {
        List<Vehicle> out = new ArrayList<>(in);
        Collections.sort(out, new Comparator<Vehicle>() {
            @Override
            public int compare(Vehicle a, Vehicle b) {
                return a.plate.compareTo(b.plate);
            }
        });
        return out;
    }
}
