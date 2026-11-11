package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure helpers for the supported-city list (testable without Android). */
public final class Cities {

    private Cities() {
    }

    public static List<City> parse(String json) {
        List<City> out = new ArrayList<>();
        List<Object> arr;
        try {
            arr = MiniJson.asArray(MiniJson.parse(json));
        } catch (RuntimeException ex) {
            return out;
        }
        if (arr == null) return out;
        for (Object o : arr) {
            Map<String, Object> m = MiniJson.asObject(o);
            if (m == null) continue;
            out.add(new City(
                    MiniJson.asInt(m.get("id"), 0),
                    MiniJson.asString(m.get("slug"), ""),
                    MiniJson.asString(m.get("name"), "?"),
                    MiniJson.asDouble(m.get("lat"), Geo.CITY_LAT),
                    MiniJson.asDouble(m.get("lon"), Geo.CITY_LON),
                    MiniJson.asInt(m.get("routes"), 0),
                    MiniJson.asInt(m.get("stops"), 0)));
        }
        return out;
    }

    /** Nearest supported city, or null when the point is farther than maxKm. */
    public static City nearest(List<City> cities, double lat, double lon, double maxKm) {
        City best = null;
        double bestD = Double.MAX_VALUE;
        for (City c : cities) {
            double d = Geo.distanceMeters(lat, lon, c.lat, c.lon);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return bestD <= maxKm * 1000 ? best : null;
    }

    public static City byId(List<City> cities, int id) {
        for (City c : cities) if (c.id == id) return c;
        return null;
    }
}
