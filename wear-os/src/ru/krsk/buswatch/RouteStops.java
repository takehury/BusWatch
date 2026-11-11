package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Ordered stops of a route in both directions. */
public final class RouteStops {
    public final int routeId;
    public final List<StopInfo> dir0; // direction 0, ordered
    public final List<StopInfo> dir1; // direction 1, ordered

    public RouteStops(int routeId, List<StopInfo> dir0, List<StopInfo> dir1) {
        this.routeId = routeId;
        this.dir0 = Collections.unmodifiableList(new ArrayList<>(dir0 == null ? new ArrayList<StopInfo>() : dir0));
        this.dir1 = Collections.unmodifiableList(new ArrayList<>(dir1 == null ? new ArrayList<StopInfo>() : dir1));
    }

    public List<StopInfo> direction(int dir) {
        return dir == 0 ? dir0 : dir1;
    }

    public boolean hasData() {
        return !dir0.isEmpty() || !dir1.isEmpty();
    }

    static List<StopInfo> parseStops(List<Object> arr) {
        List<StopInfo> out = new ArrayList<>();
        if (arr == null) return out;
        for (Object o : arr) {
            Map<String, Object> m = MiniJson.asObject(o);
            if (m == null) continue;
            out.add(new StopInfo(
                    MiniJson.asLong(m.get("id"), 0),
                    MiniJson.asString(m.get("name"), ""),
                    "",
                    MiniJson.asDouble(m.get("lat"), Double.NaN),
                    MiniJson.asDouble(m.get("lon"), Double.NaN),
                    null));
        }
        return out;
    }
}
