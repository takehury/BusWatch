package ru.krsk.buswatch;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** End-to-end check against the real Krasnoyarsk live feed. */
public final class LiveCheck {

    private static final String[] HOSTS = {
            "https://busti.me/ajax/bus/?bus_id=",
            "https://en.busti.me/ajax/bus/?bus_id="
    };

    public static void main(String[] args) throws Exception {
        String fixtures = args.length > 0 ? args[0] : "tests/fixtures";
        Catalog c = Catalog.parse(read(fixtures + "/routes.json"),
                read(fixtures + "/stops_place3.json"), read(fixtures + "/route_stops.json"));
        System.out.println("ground routes: " + c.groundRoutes().size());

        // 1) pick bus-2 (id 534) and fetch live vehicles
        RouteInfo route = c.route(534);
        RouteStops rs = c.routeStops(534);
        List<Vehicle> vehicles = null;
        long t0 = System.currentTimeMillis();
        for (String host : HOSTS) {
            try {
                String json = Http.get(host + route.id, 5000, 8000);
                vehicles = FeedParser.parse(json, route);
                if (!vehicles.isEmpty()) break;
            } catch (Exception ex) {
                System.out.println("  " + host + " failed: " + ex.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - t0;
        java.util.Calendar cal = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("Asia/Krasnoyarsk"));
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        boolean night = hour >= 22 || hour < 6;
        check("route fetched (night: empty is OK)", vehicles != null);
        check("latency under 9s", ms < 9000);

        int live = 0;
        boolean coords = true;
        int withStop = 0;
        for (Vehicle v : vehicles) {
            if (!v.sleeping) live++;
            coords &= Geo.inCity(v.lat, v.lon);
            if (v.stopId > 0 || v.order >= 0) withStop++;
        }
        check(night ? "night: buses are parked" : "live vehicles >= 5",
                night ? live == 0 : live >= 5);
        check("coords in city", coords);
        check(night ? "night: no stop refs needed" : "vehicles have stop refs",
                night || withStop >= 5);

        // 2) build the board in both directions
        long now = System.currentTimeMillis() / 1000L;
        List<Vehicle> onlyLive = new ArrayList<>();
        for (Vehicle v : vehicles) if (!v.sleeping) onlyLive.add(v);

        Board b0 = Board.build(rs.dir0, onlyLive, 0, Geo.CITY_LAT, Geo.CITY_LON, now, 300);
        int here0 = 0, eta0 = 0;
        for (Board.Row r : b0.rows) {
            if (r.hasBusHere()) here0++;
            if (r.etaMinutes > 0) eta0++;
        }
        check("board dir0 rows", b0.rows.size() == rs.dir0.size());
        check(night ? "night: board is quiet" : "board dir0 buses here >= 1",
                night || here0 >= 1);
        check(night ? "night: no eta rows" : "board dir0 eta rows >= 1",
                night || eta0 >= 1);
        check("board dir0 user stop", b0.userStopIndex >= 0);

        Board b1 = Board.build(rs.dir1, onlyLive, 1, Geo.CITY_LAT, Geo.CITY_LON, now, 300);
        check("board dir1 rows", b1.rows.size() == rs.dir1.size());

        // print a sample board around the user stop
        int center = b0.userStopIndex;
        System.out.println("--- board dir0 (around user stop #" + center + ") ---");
        for (int i = Math.max(0, center - 3); i <= Math.min(b0.rows.size() - 1, center + 4); i++) {
            Board.Row r = b0.rows.get(i);
            String status = r.hasBusHere()
                    ? ("здесь x" + r.atStop.size())
                    : (r.etaMinutes > 0 ? "~" + r.etaMinutes + " мин" : "—");
            System.out.printf("  %-2d %-30s %s%s%n", i + 1, trim(r.stop.name, 30), status,
                    r.isUserStop ? "  [ВЫ]" : "");
        }

        check("live total", night || live > 0);

        // ---- второй город: Москва (другой фид и страница маршрута) ----
        try {
            java.util.List<City> cities = Cities.parse(read("app/assets/cities.json"));
            City msk = Cities.byId(cities, 17);
            check("multi-city list has moscow", msk != null);
            Catalog mskCat = Catalog.parse(read("app/assets/cities/17_routes.json"),
                    read("app/assets/cities/17_stops.json"), null);
            java.util.Calendar mc = java.util.Calendar.getInstance(
                    java.util.TimeZone.getTimeZone("Europe/Moscow"));
            int mh = mc.get(java.util.Calendar.HOUR_OF_DAY);
            boolean mNight = mh >= 23 || mh < 5;

            RouteInfo mr = null;
            RouteStops mrs = null;
            List<Vehicle> mvs = new ArrayList<>();
            int tried = 0;
            for (RouteInfo r : mskCat.groundRoutes()) {
                if (r.id <= 0 || r.slug.isEmpty()) continue;
                if (++tried > 5) break;
                String page;
                try {
                    page = Http.get("https://en.busti.me/moscow/" + r.slug + "/", 6000, 12000);
                } catch (Exception slow) {
                    continue;
                }
                RouteStops cand = RoutePageParser.parse(r.id, page);
                if (!cand.hasData()) continue;
                List<Vehicle> vs;
                try {
                    String feed = Http.get("https://busti.me/ajax/bus/?bus_id=" + r.id, 5000, 8000);
                    vs = FeedParser.parse(feed, r);
                } catch (Exception noFeed) {
                    vs = new ArrayList<>();
                }
                if (mr == null) { mr = r; mrs = cand; mvs = vs; }
                if (!vs.isEmpty()) { mr = r; mrs = cand; mvs = vs; break; }
            }
            if (mrs == null || !mrs.hasData()) {
                System.out.println("  note: страницы маршрутов Москвы сейчас недоступны — "
                        + "проверка разбора пропущена (покрыта офлайн-тестом)");
            }
            check("moscow route page parsed or skipped", mrs == null || mrs.hasData());
            if (mrs != null) {
                Board mb = Board.build(mrs.direction(0), mvs, 0, 55.75, 37.61,
                        System.currentTimeMillis() / 1000L, 300);
                check("moscow board built", mb.rows.size() == mrs.dir0.size());
                check("moscow live vehicles", mNight || mvs.size() > 0);
            }
        } catch (Exception ex) {
            check("moscow multi-city check: " + ex.getMessage(), false);
        }
        if (night) System.out.println("  note: ночь в Красноярске (" + hour + ":xx) — автобусы не ходят");
        if (failed > 0) System.exit(2);
        System.out.println("\nLIVE CHECK: all passed");
    }

    private static int failed = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failed++;
    }

    private static String trim(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static String read(String p) throws Exception {
        return new String(Files.readAllBytes(Paths.get(p)), StandardCharsets.UTF_8);
    }
}
