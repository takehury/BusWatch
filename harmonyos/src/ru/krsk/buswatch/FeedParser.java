package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Parses the live route snapshot returned by /ajax/bus/?bus_id=... */
public final class FeedParser {

    private FeedParser() {
    }

    public static List<Vehicle> parse(String json, RouteInfo route) {
        List<Vehicle> out = new ArrayList<>();
        Map<String, Object> root;
        try {
            root = MiniJson.asObject(MiniJson.parse(json));
        } catch (RuntimeException ex) {
            return out;
        }
        if (root == null) return out;
        Map<String, Object> bd = MiniJson.asObject(root.get("bdata_mode0"));
        if (bd == null) return out;
        List<Object> list = MiniJson.asArray(bd.get("l"));
        if (list == null) return out;
        for (Object o : list) {
            Map<String, Object> m = MiniJson.asObject(o);
            if (m == null) continue;
            double lon = MiniJson.asDouble(m.get("x"), Double.NaN);
            double lat = MiniJson.asDouble(m.get("y"), Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            if (lat == 0 || lon == 0) continue;
            boolean sleeping = MiniJson.asBool(m.get("sleep"), false);
            long ts = MiniJson.asLong(m.get("ts"), 0);
            int dir = MiniJson.asInt(m.get("d"), 0);
            out.add(new Vehicle(
                    MiniJson.asString(m.get("u"), ""),
                    route.id,
                    route.type,
                    route.name,
                    route.title,
                    lat, lon,
                    MiniJson.asInt(m.get("s"), 0),
                    MiniJson.asInt(m.get("h"), 0),
                    MiniJson.asString(m.get("bn"), ""),
                    MiniJson.asString(m.get("g"), ""),
                    dir,
                    ts,
                    sleeping,
                    MiniJson.asLong(m.get("b"), 0),
                    MiniJson.asInt(m.get("order"), -1)));
        }
        return Board.unique(out);
    }

    /** Merges feeds, drops stale/parked/far vehicles and sorts by distance. */
    public static List<Vehicle> buildList(List<Vehicle> input, double lat, double lon,
                                          long nowSec, int maxAgeSec, double maxMeters, int limit) {
        List<Vehicle> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Vehicle v : input) {
            if (v.sleeping) continue;
            if (v.lat == 0 || v.lon == 0) continue;
            if (v.timestampSec > 0 && nowSec - v.timestampSec > maxAgeSec) continue;
            if (!Geo.inCity(v.lat, v.lon)) continue;
            double d = Geo.distanceMeters(lat, lon, v.lat, v.lon);
            if (d > maxMeters) continue;
            if (seen.contains(v.key())) continue;
            seen.add(v.key());
            v.distanceMeters = d;
            out.add(v);
        }
        Collections.sort(out, new java.util.Comparator<Vehicle>() {
            @Override
            public int compare(Vehicle a, Vehicle b) {
                return Double.compare(a.distanceMeters, b.distanceMeters);
            }
        });
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }
}
