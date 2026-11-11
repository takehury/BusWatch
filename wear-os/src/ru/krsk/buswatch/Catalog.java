package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Offline catalogue of Krasnoyarsk stops and routes, parsed once from assets.
 * Holds the route registry, the city stop registry and per-route ordered stop
 * lists in both directions.
 */
public final class Catalog {

    public final List<RouteInfo> routes;
    public final List<StopInfo> stops;
    private final Map<Integer, RouteInfo> byId;
    private final Map<Integer, RouteStops> routeStopsById;
    private final List<RouteInfo> groundRoutes;

    private Catalog(List<RouteInfo> routes, List<StopInfo> stops, Map<Integer, RouteStops> routeStops) {
        this.routes = Collections.unmodifiableList(routes);
        this.stops = Collections.unmodifiableList(stops);
        Map<Integer, RouteInfo> map = new HashMap<>();
        for (RouteInfo r : routes) {
            if (r.id > 0) map.put(r.id, r);
        }
        this.byId = map;
        this.routeStopsById = routeStops == null ? new HashMap<Integer, RouteStops>() : routeStops;

        List<RouteInfo> ground = new ArrayList<>();
        for (RouteInfo r : routes) {
            // routes without bundled stops are still selectable: their ordered
            // stops are fetched from the route page on demand
            if (r.isGround() && r.id > 0) ground.add(r);
        }
        Collections.sort(ground, new Comparator<RouteInfo>() {
            @Override
            public int compare(RouteInfo a, RouteInfo b) {
                if (a.type != b.type) return Integer.compare(a.type, b.type);
                Integer na = numeric(a.name);
                Integer nb = numeric(b.name);
                if (na != null && nb != null && !na.equals(nb)) return Integer.compare(na, nb);
                return a.name.compareTo(b.name);
            }
        });
        this.groundRoutes = Collections.unmodifiableList(ground);
    }

    public RouteInfo route(int id) {
        return byId.get(id);
    }

    /** True when this city ships pre-scraped ordered stops (offline mode). */
    public boolean hasBundledStops() {
        return !routeStopsById.isEmpty();
    }

    public RouteStops routeStops(int id) {
        RouteStops rs = routeStopsById.get(id);
        return rs == null ? new RouteStops(id, new ArrayList<StopInfo>(), new ArrayList<StopInfo>()) : rs;
    }

    /** Selectable ground routes with known stop lists, sorted by type then number. */
    public List<RouteInfo> groundRoutes() {
        return groundRoutes;
    }

    public static Catalog parse(String routesJson, String stopsJson) {
        return parse(routesJson, stopsJson, null);
    }

    public static Catalog parse(String routesJson, String stopsJson, String routeStopsJson) {
        List<RouteInfo> routes = parseRoutes(routesJson);
        List<StopInfo> stops = parseStops(stopsJson);
        Map<Integer, RouteStops> rs = parseRouteStops(routeStopsJson);
        return new Catalog(routes, stops, rs);
    }

    static List<RouteInfo> parseRoutes(String json) {
        List<RouteInfo> out = new ArrayList<>();
        List<Object> arr = MiniJson.asArray(MiniJson.parse(json));
        if (arr == null) return out;
        for (Object o : arr) {
            Map<String, Object> m = MiniJson.asObject(o);
            if (m == null) continue;
            out.add(new RouteInfo(
                    MiniJson.asInt(m.get("id"), 0),
                    MiniJson.asString(m.get("name"), "?"),
                    MiniJson.asInt(m.get("type"), 0),
                    MiniJson.asString(m.get("slug"), ""),
                    MiniJson.asString(m.get("title"), "")));
        }
        return out;
    }

    static List<StopInfo> parseStops(String json) {
        List<StopInfo> out = new ArrayList<>();
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(json));
        if (root == null) return out;
        List<Object> arr = MiniJson.asArray(root.get("r"));
        if (arr == null) return out;
        for (Object o : arr) {
            List<Object> e = MiniJson.asArray(o);
            if (e == null || e.size() < 5) continue;
            String name = MiniJson.asString(e.get(0), "");
            String url = MiniJson.asString(e.get(1), "");
            List<Object> coords = MiniJson.asArray(e.get(3));
            List<Object> idx = MiniJson.asArray(e.get(4));
            if (coords == null || coords.size() < 2) continue;
            double lon = MiniJson.asDouble(coords.get(0), Double.NaN);
            double lat = MiniJson.asDouble(coords.get(1), Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            int[] routeIdx;
            if (idx == null) {
                routeIdx = new int[0];
            } else {
                routeIdx = new int[idx.size()];
                for (int i = 0; i < idx.size(); i++) routeIdx[i] = MiniJson.asInt(idx.get(i), -1);
            }
            String slug = url.endsWith("/")
                    ? url.substring(0, url.length() - 1) : url;
            int slash = slug.lastIndexOf('/');
            if (slash >= 0) slug = slug.substring(slash + 1);
            out.add(new StopInfo(name, slug, lat, lon, routeIdx));
        }
        return out;
    }

    static Map<Integer, RouteStops> parseRouteStops(String json) {
        Map<Integer, RouteStops> out = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return out;
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(json));
        if (root == null) return out;
        for (Map.Entry<String, Object> e : root.entrySet()) {
            Map<String, Object> m = MiniJson.asObject(e.getValue());
            if (m == null) continue;
            int id;
            try {
                id = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            List<Object> d0 = MiniJson.asArray(m.get("dir0"));
            List<Object> d1 = MiniJson.asArray(m.get("dir1"));
            out.put(id, new RouteStops(id, RouteStops.parseStops(d0), RouteStops.parseStops(d1)));
        }
        return out;
    }

    private static Integer numeric(String name) {
        int i = 0;
        while (i < name.length() && Character.isDigit(name.charAt(i))) i++;
        if (i == 0) return null;
        try {
            return Integer.parseInt(name.substring(0, i));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Picks the live route feeds that can show buses around a point:
     * nearest stops first, then the routes serving them. The cap keeps the
     * watch's radio usage sane (one small HTTPS request per route).
     */
    public List<Integer> routesNear(double lat, double lon, int maxRoutes, double radiusM) {
        final List<StopDistance> near = new ArrayList<>();
        for (StopInfo s : stops) {
            double d = Geo.distanceMeters(lat, lon, s.lat, s.lon);
            if (d <= radiusM) near.add(new StopDistance(s, d));
        }
        if (near.size() < 3) {
            near.clear();
            for (StopInfo s : stops) {
                near.add(new StopDistance(s, Geo.distanceMeters(lat, lon, s.lat, s.lon)));
            }
        }
        Collections.sort(near, new Comparator<StopDistance>() {
            @Override
            public int compare(StopDistance a, StopDistance b) {
                return Double.compare(a.distance, b.distance);
            }
        });

        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        int stopBudget = Math.min(near.size(), 10);
        for (int i = 0; i < stopBudget && ids.size() < maxRoutes; i++) {
            for (int idx : near.get(i).stop.routeIdx) {
                if (idx < 0 || idx >= routes.size()) continue;
                RouteInfo r = routes.get(idx);
                if (r.id <= 0 || !r.isGround()) continue;
                ids.add(r.id);
                if (ids.size() >= maxRoutes) break;
            }
        }
        return new ArrayList<>(ids);
    }

    public List<StopInfo> nearestStops(double lat, double lon, int count) {
        List<StopDistance> all = new ArrayList<>();
        for (StopInfo s : stops) {
            all.add(new StopDistance(s, Geo.distanceMeters(lat, lon, s.lat, s.lon)));
        }
        Collections.sort(all, new Comparator<StopDistance>() {
            @Override
            public int compare(StopDistance a, StopDistance b) {
                return Double.compare(a.distance, b.distance);
            }
        });
        List<StopInfo> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, all.size()); i++) out.add(all.get(i).stop);
        return out;
    }

    private static final class StopDistance {
        final StopInfo stop;
        final double distance;

        StopDistance(StopInfo stop, double distance) {
            this.stop = stop;
            this.distance = distance;
        }
    }
}
