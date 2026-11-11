package ru.krsk.buswatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Host-side test suite (no Android, no emulator, no external test deps). */
public final class AllTests {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        String fixtures = args.length > 0 ? args[0] : "tests/fixtures";
        String stopsJson = read(fixtures + "/stops_place3.json");
        String routesJson = read(fixtures + "/routes.json");
        String routeStopsJson = read(fixtures + "/route_stops.json");
        String bus534 = read(fixtures + "/bus_534.json");

        testJson();
        testGeo();
        testText();
        testCatalog(stopsJson, routesJson, routeStopsJson);
        testFeed(bus534);
        testSelection();
        testFocalList();
        testAlert();
        testBoard(stopsJson, routesJson, routeStopsJson, bus534);
        testRotary();
        testAlertText();
        testDisplays();
        testInertia();
        testRoutePageParser();
        testMultiCity();
        testWatchState(stopsJson, routesJson, routeStopsJson, bus534);
        testUi(stopsJson, routesJson, routeStopsJson, bus534);

        System.out.println();
        System.out.println("TESTS: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void testJson() {
        Object o = MiniJson.parse("{\"a\":1,\"b\":[true,null,\"x\\u0416\"],\"c\":{\"d\":-2.5e2}}");
        java.util.Map<String, Object> m = MiniJson.asObject(o);
        check("json object", m != null && m.size() == 3);
        check("json number", MiniJson.asInt(m.get("a"), 0) == 1);
        List<Object> arr = MiniJson.asArray(m.get("b"));
        check("json array", arr != null && arr.size() == 3 && Boolean.TRUE.equals(arr.get(0)));
        check("json null", arr != null && arr.get(1) == null);
        check("json unicode", arr != null && "xЖ".equals(arr.get(2)));
        java.util.Map<String, Object> c = MiniJson.asObject(m.get("c"));
        check("json nested", c != null && MiniJson.asDouble(c.get("d"), 0) == -250.0);
        boolean threw = false;
        try {
            MiniJson.parse("{\"a\":}");
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check("json rejects broken input", threw);
    }

    private static void testGeo() {
        double d = Geo.distanceMeters(56.0106, 92.8526, 56.0196, 92.8526);
        check("haversine ~1km", d > 980 && d < 1020);
        check("distance zero", Geo.distanceMeters(56.0, 92.8, 56.0, 92.8) == 0);
        check("format 850m", "850 м".equals(Geo.formatDistance(850)));
        check("format 1234m", "1,2 км".equals(Geo.formatDistance(1234)));
        check("format 12km", "12 км".equals(Geo.formatDistance(12345)));
        check("eta 1km", Geo.etaMinutes(1000) == 4);
        check("eta min", Geo.etaMinutes(10) == 1);
        check("in city", Geo.inCity(56.01, 92.85));
        check("outside city", !Geo.inCity(55.0, 92.85));
        check("compass", "С".equals(Geo.compass(0)) && "В".equals(Geo.compass(90)));
    }

    private static void testText() {
        check("plural 1", "минута".equals(Text.plural(1, "минута", "минуты", "минут")));
        check("plural 2", "минуты".equals(Text.plural(2, "минута", "минуты", "минут")));
        check("plural 5", "минут".equals(Text.plural(5, "минута", "минуты", "минут")));
        check("plural 11", "минут".equals(Text.plural(11, "минута", "минуты", "минут")));
        check("plural 21", "минута".equals(Text.plural(21, "минута", "минуты", "минут")));
        check("ellipsis", "Авто…".equals(Text.ellipsize("Автовокзал", 5)));
        check("ellipsis short", "Авто".equals(Text.ellipsize("Авто", 5)));
        check("clock format", Text.clock(0).matches("\\d\\d:\\d\\d"));
    }

    private static void testCatalog(String stopsJson, String routesJson, String routeStopsJson) {
        Catalog c = Catalog.parse(routesJson, stopsJson, routeStopsJson);
        check("catalog routes 160", c.routes.size() == 160);
        check("catalog stops 999", c.stops.size() == 999);
        check("catalog ground routes 74", c.groundRoutes().size() == 74);
        check("route id 534", c.route(534) != null && "2".equals(c.route(534).name));

        RouteStops rs = c.routeStops(534);
        check("route 534 stops dir0", rs.dir0.size() == 51);
        check("route 534 stops dir1", rs.dir1.size() == 52);
        check("route 534 first stop", "Автовокзал Восточный".equals(rs.dir0.get(0).name));
        check("route stops have coords", rs.dir0.get(0).lat > 50 && rs.dir0.get(0).lon > 90);

        // ground routes sorted: buses first, then trolley, tram, minibus
        List<RouteInfo> g = c.groundRoutes();
        boolean sortedOk = true;
        for (int i = 1; i < g.size(); i++) sortedOk &= g.get(i - 1).type <= g.get(i).type;
        check("ground routes sorted by type", sortedOk);
        boolean hasTrolley = false;
        boolean hasTram = false;
        for (RouteInfo r : g) {
            if (r.type == 1) hasTrolley = true;
            if (r.type == 2) hasTram = true;
        }
        check("ground includes trolley", hasTrolley);
        check("ground includes tram", hasTram);
    }

    private static void testFeed(String bus534) {
        RouteInfo r = new RouteInfo(534, "2", 0, "bus-2", "Автовокзал Восточный ↔ Дом учёных");
        List<Vehicle> vs = FeedParser.parse(bus534, r);
        check("feed parses vehicles", vs.size() == 17);
        boolean coords = true;
        boolean sleeping = false;
        for (Vehicle v : vs) {
            coords &= Geo.inCity(v.lat, v.lon);
            sleeping |= v.sleeping;
        }
        check("feed coords in city", coords);
        check("feed keeps sleeping flag", sleeping);
        boolean hasStopId = false;
        for (Vehicle v : vs) {
            if (v.stopId > 0) hasStopId = true;
        }
        check("feed has stop ids", hasStopId);
        boolean hasOrder = false;
        for (Vehicle v : vs) {
            if (v.order >= 0) hasOrder = true;
        }
        check("feed has stop order", hasOrder);

        check("broken feed empty", FeedParser.parse("not json", r).isEmpty());
        check("empty feed empty", FeedParser.parse("{}", r).isEmpty());
    }

    private static void testSelection() {
        Selection sel = new Selection();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 12; i++) keys.add(String.valueOf(i));
        sel.setKeys(keys);
        check("sel size", sel.size() == 12);
        check("sel starts 0", sel.selectedIndex() == 0);
        check("sel top centres first", sel.topIndex() == -2);
        sel.move(3);
        check("sel move", sel.selectedIndex() == 3);
        check("sel centers", sel.topIndex() == 1);
        sel.move(-99);
        check("sel clamp top", sel.selectedIndex() == 0);
        sel.move(99);
        check("sel clamp bottom", sel.selectedIndex() == 11);
        check("sel bottom window", sel.topIndex() == 9);
        check("sel more above", sel.hasMoreAbove());
        check("sel no more below", !sel.hasMoreBelow());
        sel.selectRow(2);
        check("sel selectRow", sel.selectedIndex() == 11);

        // preserve key on refill
        List<String> keys2 = new ArrayList<>(keys);
        sel.setKeys(keys2);
        check("sel preserves key", "11".equals(sel.selectedKey()));
        sel.setKeys(new ArrayList<String>());
        check("sel empty", sel.isEmpty() && sel.size() == 0);
    }

    private static void testBoard(String stopsJson, String routesJson, String routeStopsJson, String bus534) {
        Catalog c = Catalog.parse(routesJson, stopsJson, routeStopsJson);
        RouteInfo route = c.route(534);
        RouteStops rs = c.routeStops(534);
        List<Vehicle> vs = FeedParser.parse(bus534, route);
        long now = 1794275200L;
        List<Vehicle> live = new ArrayList<>();
        for (Vehicle v : vs) {
            if (!v.sleeping) live.add(v);
        }
        Board b0 = Board.build(rs.dir0, live, 0, Geo.CITY_LAT, Geo.CITY_LON, now, 300);
        check("board rows = stops", b0.rows.size() == rs.dir0.size());
        boolean hasHere = false;
        boolean hasEta = false;
        for (Board.Row row : b0.rows) {
            if (row.hasBusHere()) hasHere = true;
            if (row.etaMinutes > 0) hasEta = true;
        }
        check("board marks buses here", hasHere);
        check("board has eta", hasEta);
        check("board user stop set", b0.userStopIndex >= 0);
        check("board user row flagged", b0.rows.get(b0.userStopIndex).isUserStop);

        // circular: direction 1 empty -> fallback handled by caller; board with dir1 vehicles only
        Board b1 = Board.build(rs.dir1, live, 1, Geo.CITY_LAT, Geo.CITY_LON, now, 300);
        check("board dir1 rows", b1.rows.size() == rs.dir1.size());

        // ETA logic: bus at stop index k -> that row eta 0
        int k = 5;
        List<Vehicle> one = new ArrayList<>();
        Vehicle v = new Vehicle("u", 534, 0, "2", route.title, rs.dir0.get(k).lat, rs.dir0.get(k).lon,
                20, 0, rs.dir0.get(k).name, "А123ВС", 0, now, false, rs.dir0.get(k).id, k);
        one.add(v);
        Board single = Board.build(rs.dir0, one, 0, Geo.CITY_LAT, Geo.CITY_LON, now, 300);
        check("single bus at stop", single.rows.get(k).hasBusHere() && single.rows.get(k).etaMinutes == 0);
        check("stop after bus has eta", single.rows.get(k + 1).etaMinutes >= 1);
    }

    private static void testRotary() {
        Rotary r = new Rotary();
        check("rotary single", r.onRotate(1f) == 1);
        check("rotary reverse", r.onRotate(-1f) == -1);
        check("rotary below threshold", r.onRotate(0.4f) == 0 && r.onRotate(0.2f) == 0);
        check("rotary accumulates", r.onRotate(0.4f) == 1);
        r.reset();
        check("rotary cap", r.onRotate(50f) == 3);
        check("rotary stale clamp", Math.abs(r.accumulator()) <= Rotary.STEP);
        check("rotary ignores nan", r.onRotate(Float.NaN) == 0);
    }

    private static void testFocalList() {
        check("focal visible 5", FocalList.VISIBLE == 5);
        check("focal centre scale 1", FocalList.scale(2) == 1.0f);
        check("focal edge smaller", FocalList.scale(0) < FocalList.scale(2)
                && FocalList.scale(4) < FocalList.scale(2));
        check("focal centre tallest", FocalList.rowHeightDp(2) > FocalList.rowHeightDp(0)
                && FocalList.rowHeightDp(2) > FocalList.rowHeightDp(1));
        check("focal total height", Math.abs(FocalList.totalHeightDp() - 136f) < 0.01f);
        check("focal rowAt centre", FocalList.rowAt(0f, 21f + 29f + 10f) == 2);
        check("focal rowAt top", FocalList.rowAt(0f, 5f) == 0);
        check("focal rowAt outside", FocalList.rowAt(0f, 999f) == -1);
        // width also tapers toward the edges
        check("focal width centre widest", FocalList.rowWidthDp(168f, 2) > FocalList.rowWidthDp(168f, 0)
                && FocalList.rowWidthDp(168f, 2) > FocalList.rowWidthDp(168f, 1));
        check("focal width max", FocalList.rowWidthDp(168f, 2) == 168f);
        check("focal width symmetric", FocalList.rowWidthDp(168f, 0) == FocalList.rowWidthDp(168f, 4));
        check("focal rows have a gap", FocalList.rowHeightDp(2) < 40f
                && FocalList.rowHeightDp(2) == 40f - FocalList.GAP);
        // route picker: taller slots, bigger gaps, still inside the circle
        check("route list total height", Math.abs(RouteList.totalHeightDp() - 174f) < 0.01f);
        check("route list gap", RouteList.rowHeightDp(2) == 46f - RouteList.GAP);
        check("route gap bigger than stops", RouteList.GAP > FocalList.GAP);
        check("route rows fit circle", routeRowsInsideCircle(29.5f, 168f, 112f));
        check("route centre tallest", RouteList.rowHeightDp(2) > RouteList.rowHeightDp(1));
        // spacing: the 1-2 gap must equal the last pair gap, and every visual
        // gap between neighbouring rows must be identical
        float d12 = RouteList.rowCenterDp(29.5f, 1) - RouteList.rowCenterDp(29.5f, 0);
        float d45 = RouteList.rowCenterDp(29.5f, 4) - RouteList.rowCenterDp(29.5f, 3);
        check("route 1-2 spacing == last pair", Math.abs(d12 - d45) < 0.01f);
        boolean uniformGaps = true;
        for (int i = 1; i < RouteList.VISIBLE; i++) {
            float prevBottom = RouteList.rowTopDp(29.5f, i - 1) + RouteList.rowHeightDp(i - 1);
            float gap = RouteList.rowTopDp(29.5f, i) - prevBottom;
            uniformGaps &= Math.abs(gap - RouteList.GAP) < 0.01f;
        }
        check("route visual gaps uniform", uniformGaps);
        float topMargin = (RouteList.rowTopDp(29.5f, 0) + RouteList.GAP / 2f) - 4.5f;
        float bottomMargin = 228.5f - (RouteList.rowTopDp(29.5f, 4)
                + RouteList.GAP / 2f + RouteList.rowHeightDp(4));
        check("route list vertically balanced", Math.abs(topMargin - bottomMargin) < 6f);
        // row sides follow the round display: longer than the old fixed-width rows
        float wTop = FocalList.widthForRow(RouteList.rowTopDp(29.5f, 0), RouteList.rowHeightDp(0), 112f, 116.5f, 9f, 214f);
        float wMid = FocalList.widthForRow(RouteList.rowTopDp(29.5f, 2), RouteList.rowHeightDp(2), 112f, 116.5f, 9f, 214f);
        float wBot = FocalList.widthForRow(RouteList.rowTopDp(29.5f, 4), RouteList.rowHeightDp(4), 112f, 116.5f, 9f, 214f);
        check("route centre row longer at sides", wMid > 190f);
        check("route width circle-shaped", wMid > wTop && wMid > wBot);
        check("route widths stay inside circle", wTop < 214f && wBot < 214f);
        float swTop = FocalList.widthForRow(FocalList.rowTopDp(68f, 4), FocalList.rowHeightDp(4), 112f, 116.5f, 9f, 214f);
        check("stop rows follow circle", swTop > 100f && swTop < 214f);

        // topIndex for 12 items, selected 3 -> centre at visible index 2
        check("focal topIndex", FocalList.topIndex(12, 3) == 1);
        check("focal topIndex clamp", FocalList.topIndex(12, 11) == 9);
        check("focal topIndex empty", FocalList.topIndex(0, 0) == 0);
        check("first item centred", FocalList.topIndex(12, 0) == -2);
        check("last item centred", FocalList.topIndex(12, 11) == 9);
        check("short list centred", FocalList.topIndex(3, 0) == -1
                && FocalList.topIndex(3, 2) == -1);
        check("single item centred", FocalList.topIndex(1, 0) == -2);

        // the row rectangles themselves must stay inside the round screen
        check("focal rows fit stops circle", rowsInsideCircle(68f, 168f, 112f));
        check("stop rows clear the scrollbar", rowsClearScrollbar(68f, 136));
        check("route rows clear the scrollbar", routeRowsClearScrollbar(29.5f));
        check("focal widths shrink at edges", FocalList.rowWidthDp(168f, 0) < 168f * 0.85f);
    }

    private static void testAlert() {
        Alert a = new Alert(5);
        check("alert not fired initially", !a.isFired());
        check("alert ignores bus far before", !a.check(orders("v1", 1)));
        check("alert ignores bus approaching", !a.check(orders("v1", 4)));
        check("alert fires when bus at stop", a.check(orders("v1", 5)));
        check("alert fired once", a.isFired());

        // a bus that was before the stop and skipped past it between polls
        Alert b = new Alert(5);
        check("alert no fire for already past", !b.check(orders("x", 8)));
        check("alert no fire for unknown past", !b.check(orders("y", 9)));
        Alert c = new Alert(5);
        check("alert tracks before", !c.check(orders("z", 4)));
        check("alert fires when same bus passes", c.check(orders("z", 7)));

        // another bus being past must not fire an alert for a bus before
        Alert d = new Alert(5);
        check("alert other bus before", !d.check(two("a", 4, "b", 9)));
        check("alert other bus stays past", !d.check(two("a", 4, "b", 10)));
        check("alert fires only when a reaches", d.check(two("a", 5, "b", 11)));
    }

    private static java.util.Map<String, Integer> orders(String id, int order) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        m.put(id, order);
        return m;
    }

    private static java.util.Map<String, Integer> two(String id1, int o1, String id2, int o2) {
        java.util.Map<String, Integer> m = orders(id1, o1);
        m.put(id2, o2);
        return m;
    }

    private static void testInertia() {
        // отрицательная скорость — движение вверх по списку (вперёд)
        Inertia up = new Inertia(-20f);
        int moved = 0;
        for (int i = 0; i < 400 && up.active(); i++) moved += up.update(0.016f);
        check("inertia direction", moved < 0);
        check("inertia distance", Math.abs(moved) >= 1 && Math.abs(moved) <= 5);

        Inertia down = new Inertia(20f);
        int movedDown = 0;
        for (int i = 0; i < 400 && down.active(); i++) movedDown += down.update(0.016f);
        check("inertia reverse", movedDown > 0);

        // скорость затухает
        Inertia slow = new Inertia(10f);
        float before = Math.abs(slow.speed());
        slow.update(0.1f);
        check("inertia decays", Math.abs(slow.speed()) < before);

        // слабый жест не запускает инерцию
        Inertia tiny = new Inertia(0.5f);
        check("inertia too slow", !tiny.active() || tiny.update(0.016f) == 0);

        // ограничение сверху
        Inertia wild = new Inertia(100f);
        int total = 0;
        for (int i = 0; i < 1000 && wild.active(); i++) total += wild.update(0.016f);
        check("inertia capped", Math.abs(total) <= Inertia.MAX_ROWS);

        // направление: палец вверх (отрицательная скорость) — список вперёд
        check("inertia direction up", Inertia.rowsPerSecond(-2000f, 76f) > 0);
        check("inertia direction down", Inertia.rowsPerSecond(2000f, 76f) < 0);
        check("inertia direction zero pitch", Inertia.rowsPerSecond(-2000f, 0f) == 0f);

        // нулевой dt безопасен
        check("inertia zero dt", new Inertia(10f).update(0f) == 0);
    }

    private static void testDisplays() {
        check("design size", Displays.DESIGN == 466f);
        check("scale 466 = 1", Math.abs(Displays.scaleFor(466, 466) - 1f) < 0.001f);
        check("scale 360", Math.abs(Displays.scaleFor(360, 360) - 360f / 466f) < 0.001f);
        check("scale 480", Displays.scaleFor(480, 480) > 1f && Displays.scaleFor(480, 480) <= 1.25f);
        check("scale ignores tiny", Displays.scaleFor(1, 1) >= 0.6f);
        check("squareish watch", Displays.isSquareish(466, 466));
        check("squareish off", !Displays.isSquareish(466, 300));
        // известные разрешения часов 2020+ укладываются в разумный диапазон
        int[] sizes = {360, 384, 390, 396, 408, 416, 450, 454, 456, 466, 480};
        boolean sane = true;
        for (int s : sizes) {
            float k = Displays.scaleFor(s, s);
            sane &= k >= 0.75f && k <= 1.05f;
        }
        check("all watch sizes sane", sane);
    }

    private static void testRoutePageParser() throws Exception {
        String html = new String(Files.readAllBytes(
                Paths.get("tests/fixtures/route_page_nsk.html")), StandardCharsets.UTF_8);
        RouteStops rs = RoutePageParser.parse(2535, html);
        check("route page dir0", rs.dir0.size() == 32);
        check("route page dir1", rs.dir1.size() == 33);
        check("route page first stop", rs.dir0.get(0).name.contains("Вокзал"));
        check("route page coords", rs.dir0.get(0).lat > 54 && rs.dir0.get(0).lon > 82);
        check("route page broken empty", !RoutePageParser.parse(1, "no data").hasData());
    }

    private static void testMultiCity() throws Exception {
        String citiesJson = read("app/assets/cities.json");
        java.util.List<City> cities = Cities.parse(citiesJson);
        check("cities parsed", cities.size() == 22);
        City msk = null;
        for (City c : cities) if ("moscow".equals(c.slug)) msk = c;
        check("cities contain moscow", msk != null && "Москва".equals(msk.name));
        check("cities nearest moscow", Cities.nearest(cities, 55.75, 37.61, 100) == msk);
        check("cities nearest krasnoyarsk",
                "Красноярск".equals(Cities.nearest(cities, 56.01, 92.85, 100).name));

        // Новосибирск: маршруты без встроенных списков остановок
        String routes = read("app/assets/cities/23_routes.json");
        String stops = read("app/assets/cities/23_stops.json");
        Catalog nsk = Catalog.parse(routes, stops, null);
        check("nsk routes parsed", nsk.routes.size() > 200);
        check("nsk ground routes", nsk.groundRoutes().size() > 100);
        check("nsk no bundled stops", !nsk.hasBundledStops());
        check("nsk route by id", nsk.route(2535) != null && "3".equals(nsk.route(2535).name));
    }

    private static void testAlertText() {
        // the feed's internal bus id must never leak into notifications
        check("alert uses stored route number", "2".equals(AlertText.routeNumber("2", 534)));
        check("alert falls back to id", "534".equals(AlertText.routeNumber("", 534)));
        check("alert trims number", "18С".equals(AlertText.routeNumber(" 18С ", 534)));
        check("alert title", AlertText.foregroundTitle("2").contains("№2"));
        check("alert arrival text", AlertText.arrivalText("2", "пл. Революции")
                .contains("№2") && AlertText.arrivalText("2", "пл. Революции")
                .contains("пл. Революции"));
        check("alert arrival has no internal id",
                !AlertText.arrivalText("2", "пл. Революции").contains("534"));
    }

    private static void testWatchState(String stopsJson, String routesJson, String routeStopsJson, String bus534) {
        Catalog c = Catalog.parse(routesJson, stopsJson, routeStopsJson);
        WatchState s = new WatchState();
        s.setRoutes(c.groundRoutes());
        check("ws routes set", s.routes.size() == 74);

        // location defaults
        check("ws auto default", s.autoTrack && s.locationMode == WatchState.LOC_AUTO);
        check("ws eff auto uses lat", s.effLat() == Geo.CITY_LAT);
        s.lat = 56.1;
        s.lon = 92.9;
        check("ws eff auto updated", s.effLat() == 56.1);

        // manual location
        s.setManualLocation("пл. Революции", 56.0106, 92.8526);
        check("ws manual active", s.manualActive());
        check("ws manual label", "РУЧ".equals(s.locLabel()));
        check("ws manual overrides", s.effLat() == 56.0106 && s.effLon() == 92.8526);
        s.clearManualLocation();
        check("ws manual cleared", !s.manualActive() && s.locationMode == WatchState.LOC_AUTO);

        // alert arm/disarm
        s.selectRoute(c.route(534));
        s.openRoute(c.route(534), 0);
        s.armAlert(10, "Каменный квартал");
        check("ws alert armed", s.alertArmed && s.alertStopIndex == 10);
        check("ws alert armed at", s.isAlertArmedAt(10) && !s.isAlertArmedAt(11));
        s.disarmAlert();
        check("ws alert disarmed", !s.alertArmed);

        // live refresh must not steal the selection back to the user's stop
        RouteStops rs2 = c.routeStops(534);
        Board b2 = Board.build(rs2.dir0, new ArrayList<Vehicle>(), 0,
                Geo.CITY_LAT, Geo.CITY_LON, 1794275200L, 300);
        s.setBoard(rs2, b2, 5, 0);
        check("ws board first fill centres on user", s.stopSel.selectedIndex() == 5);
        s.stopSel.move(7);
        int chosen = s.stopSel.selectedIndex();
        Board b3 = Board.build(rs2.dir0, new ArrayList<Vehicle>(), 0,
                Geo.CITY_LAT, Geo.CITY_LON, 1794275200L, 300);
        s.setBoard(rs2, b3, 5, 0);
        check("ws refresh keeps selection", s.stopSel.selectedIndex() == chosen);

        // menus
        s.openStopMenu();
        check("ws stop menu", s.screen == WatchState.SCREEN_STOP_MENU && s.menuSel.size() == 3);
        s.openSettings();
        check("ws settings", s.screen == WatchState.SCREEN_SETTINGS && s.menuSel.size() == 4);
        s.closeMenu();
        check("ws menu closes to parent", s.screen == WatchState.SCREEN_ROUTES);

        // a catalogue reload while in settings must not reset the screen
        WatchState keep = new WatchState();
        keep.status = WatchState.STATUS_READY;
        keep.openSettings();
        keep.setRoutes(c.groundRoutes());
        check("setRoutes keeps settings", keep.screen == WatchState.SCREEN_SETTINGS);
        keep.openCities();
        keep.setRoutes(c.groundRoutes());
        check("setRoutes keeps city picker", keep.screen == WatchState.SCREEN_CITIES);
        keep.closeMenu();
        check("city picker back -> settings", keep.screen == WatchState.SCREEN_SETTINGS);
        keep.closeMenu();
        check("settings back -> routes", keep.screen == WatchState.SCREEN_ROUTES);

        // city picker
        java.util.List<City> cities = new java.util.ArrayList<>();
        cities.add(new City(3, "krasnoyarsk", "Красноярск", 56.01, 92.85, 160, 999));
        cities.add(new City(23, "novosibirsk", "Новосибирск", 55.03, 82.92, 251, 1282));
        s.cities.addAll(cities);
        s.city = cities.get(0);
        s.cityAuto = true;
        s.openCities();
        check("ws city screen", s.screen == WatchState.SCREEN_CITIES);
        check("ws city options", s.cityOptionsCount() == 3);
        check("ws city auto first", s.citySel.selectedIndex() == 0
                && "Авто (по GPS)".equals(s.cityOptionName(0)));
        s.cityAuto = false;
        s.city = cities.get(1);
        s.openCities();
        check("ws city manual selected", s.citySel.selectedIndex() == 2
                && "Новосибирск".equals(s.cityOptionName(2)));

        // nearest city by coordinates
        check("ws nearest city", Cities.nearest(cities, 55.0, 82.9, 200) == cities.get(1));
        check("ws nearest city none", Cities.nearest(cities, 43.1, 131.9, 200) == null);
    }

    private static void testUi(String stopsJson, String routesJson, String routeStopsJson, String bus534) {
        Catalog c = Catalog.parse(routesJson, stopsJson, routeStopsJson);
        Metrics m = new FakeMetrics();
        WatchUi ui = new WatchUi(2f, 2f, 466, 466);

        WatchState s = new WatchState();
        s.setRoutes(c.groundRoutes());
        s.routeSel.select(2); // full five-row window
        s.status = WatchState.STATUS_READY;
        s.gpsFix = true;
        UiFrame routes = ui.build(s, m);
        check("ui routes has ops", routes.ops.size() > 25);
        check("ui routes has texts", countType(routes, UiFrame.TEXT) >= 12);
        check("ui routes selection highlight", hasRoundRect(routes));
        check("ui routes scrollbar", countType(routes, UiFrame.ARC) >= 2);
        // two text lines: route title on top, vehicle type underneath
        check("ui routes shows type text", hasText(routes, "Автобус")
                || hasText(routes, "Троллейбус") || hasText(routes, "Трамвай"));
        check("ui routes has two-line rows", countType(routes, UiFrame.TEXT) >= 15);
        check("ui routes text inside", textInsideCircle(routes, m, 224f));
        check("ui routes has no city title", !hasText(routes, "КРАСНОЯРСК"));

        // stops screen
        s.selectRoute(c.route(534));
        s.openRoute(c.route(534), 0);
        List<Vehicle> live = new ArrayList<>();
        for (Vehicle v : FeedParser.parse(bus534, c.route(534))) if (!v.sleeping) live.add(v);
        Board b = Board.build(c.routeStops(534).dir0, live, 0, Geo.CITY_LAT, Geo.CITY_LON,
                1794275200L, 300);
        s.setBoard(c.routeStops(534), b, b.userStopIndex, live.size());
        UiFrame stops = ui.build(s, m);
        check("ui stops has ops", stops.ops.size() > 25);
        check("ui stops shows here/eta", countType(stops, UiFrame.TEXT) >= 12);
        check("ui stops direction toggle", hasText(stops, "⇄"));
        check("ui stops no here text", !hasText(stops, "здесь"));
        check("ui stops draws bus dots", countType(stops, UiFrame.CIRCLE) >= 5);

        // three buses on one stop -> three dots in a horizontal row, same y
        {
            RouteStops rs3 = c.routeStops(534);
            List<Vehicle> three = new ArrayList<>();
            int k = 5;
            for (int i = 0; i < 3; i++) {
                three.add(new Vehicle("b" + i, 534, 0, "2", c.route(534).title,
                        rs3.dir0.get(k).lat, rs3.dir0.get(k).lon, 10, 0, "x", "А00" + i,
                        0, 1794275200L, false, rs3.dir0.get(k).id, k));
            }
            Board b3 = Board.build(rs3.dir0, three, 0,
                    rs3.dir0.get(k).lat, rs3.dir0.get(k).lon, 1794275200L, 300);
            WatchState s3 = new WatchState();
            s3.status = WatchState.STATUS_READY;
            s3.setRoutes(c.groundRoutes());
            s3.selectRoute(c.route(534));
            s3.openRoute(c.route(534), 0);
            s3.setBoard(rs3, b3, b3.userStopIndex, 3);
            UiFrame f3 = ui.build(s3, m);
            java.util.List<Float> ys = new java.util.ArrayList<>();
            java.util.List<Float> xs = new java.util.ArrayList<>();
            for (UiFrame.Op op : f3.ops) {
                if (op.type == UiFrame.CIRCLE && op.color == 0xFF3DDC97) {
                    ys.add(op.y);
                    xs.add(op.x);
                }
            }
            boolean sameRow = false;
            int best = 0;
            for (Float y : ys) {
                int cnt = 0;
                for (Float z : ys) if (Math.abs(z - y) < 0.01f) cnt++;
                if (cnt >= 3) sameRow = true;
                best = Math.max(best, cnt);
            }
            check("three dots on one row", sameRow && best >= 3);

            // одна и та же машина, повторённая в списке, не должна давать три точки
            java.util.List<Vehicle> dup = new ArrayList<>();
            dup.add(three.get(0));
            dup.add(three.get(0));
            dup.add(three.get(0));
            check("board unique", Board.unique(dup).size() == 1);
            check("feed keeps distinct", Board.unique(three).size() == 3);
            Board bd = Board.build(rs3.dir0, dup, 0, rs3.dir0.get(k).lat, rs3.dir0.get(k).lon,
                    1794275200L, 300);
            check("duplicate bus draws one dot", bd.rows.get(k).atStop.size() == 1);
            check("three dots distinct x", new java.util.HashSet<>(xs).size() >= 3);
        }
        check("ui stops user marker", hasText(stops, "вы"));
        check("ui stops text inside", textInsideCircle(stops, m, 224f));

        // stop menu
        s.openStopMenu();
        UiFrame menu = ui.build(s, m);
        check("ui stop menu builds", menu.ops.size() > 8);
        check("ui stop menu has items", hasText(menu, "Я здесь")
                && hasText(menu, "Ждать автобус"));
        check("ui stop menu text inside", textInsideCircle(menu, m, 224f));

        // city picker
        WatchState cs = new WatchState();
        cs.status = WatchState.STATUS_READY;
        cs.cities.add(new City(3, "krasnoyarsk", "Красноярск", 56.01, 92.85, 160, 999));
        cs.cities.add(new City(23, "novosibirsk", "Новосибирск", 55.03, 82.92, 251, 1282));
        cs.city = cs.cities.get(0);
        cs.openCities();
        UiFrame cityFrame = ui.build(cs, m);
        check("ui cities builds", cityFrame.ops.size() >= 6);
        check("ui cities has auto", hasText(cityFrame, "Авто"));
        check("ui cities has no title", !hasText(cityFrame, "ГОРОД"));
        check("ui cities has names", hasText(cityFrame, "Красноярск"));
        check("ui cities inside", textInsideCircle(cityFrame, m, 224f));

        // settings
        s.openSettings();
        UiFrame settings = ui.build(s, m);
        check("ui settings builds", settings.ops.size() > 8);
        check("ui settings autotrack", hasText(settings, "Автопозиция"));
        check("ui settings alerts", hasText(settings, "Ожидание:"));
        check("ui settings text inside", textInsideCircle(settings, m, 224f));

        s.screen = WatchState.SCREEN_ROUTES;
        s.board = null;
        s.status = WatchState.STATUS_LOADING;
        check("ui loading builds", ui.build(s, m).ops.size() > 5);
        s.status = WatchState.STATUS_ERROR;
        s.message = "Нет связи с сервером";
        UiFrame err = ui.build(s, m);
        check("ui error builds", err.ops.size() > 5);
        check("ui error has back", hasText(err, "НАЗАД"));
        check("ui error has retry", hasText(err, "ПОВТОР"));
        check("ui error explains night", hasText(err, "автобусы не ходят"));
        check("ui error text inside", textInsideCircle(err, m, 224f));

        // навигация должна работать даже во время загрузки/ошибки
        WatchState nav = new WatchState();
        nav.status = WatchState.STATUS_LOADING;
        nav.screen = WatchState.SCREEN_SETTINGS;
        nav.openSettings();
        check("settings open while loading", hasText(ui.build(nav, m), "Автопозиция"));
        nav.openCities();
        check("cities open while loading", hasText(ui.build(nav, m), "Авто"));
        nav.status = WatchState.STATUS_ERROR;
        nav.screen = WatchState.SCREEN_SETTINGS;
        check("settings open on error", hasText(ui.build(nav, m), "Автопозиция"));
        s.status = WatchState.STATUS_EMPTY;
        check("ui empty builds", ui.build(s, m).ops.size() > 5);
    }

    // -------------------------------------------------------------- helpers

    /** Verifies the fisheye row rectangles fit inside the round 466px screen. */
    /** Verifies the fisheye stop rows (widths follow the circle) fit inside it. */
    private static boolean rowsInsideCircle(float listTopDp, float ignoredWidth, float radiusDp) {
        float cx = 116.5f;
        for (int row = 0; row < FocalList.VISIBLE; row++) {
            float top = FocalList.rowTopDp(listTopDp, row);
            float h = FocalList.rowHeightDp(row);
            float w = FocalList.widthForRow(top, h, radiusDp, cx, 9f, 214f);
            if (!rectInside(cx, top, h, w, radiusDp, "stop row " + row)) return false;
        }
        return true;
    }

    private static boolean routeRowsInsideCircle(float listTopDp, float ignoredWidth, float radiusDp) {
        float cx = 116.5f;
        for (int row = 0; row < RouteList.VISIBLE; row++) {
            float top = RouteList.rowTopDp(listTopDp, row);
            float h = RouteList.rowHeightDp(row);
            float w = FocalList.widthForRow(top, h, radiusDp, cx, 9f, 214f);
            if (!rectInside(cx, top, h, w, radiusDp, "route row " + row)) return false;
        }
        return true;
    }

    private static boolean rectInside(float cx, float top, float h, float w,
                                      float radiusDp, String what) {
        float halfW = w / 2f;
        float[][] corners = {
                {cx - halfW, top}, {cx + halfW, top},
                {cx - halfW, top + h}, {cx + halfW, top + h}};
        for (float[] p : corners) {
            float dx = p[0] - cx, dy = p[1] - cx;
            if (Math.sqrt(dx * dx + dy * dy) > radiusDp + 0.01f) {
                System.out.println("    " + what + " outside circle: " + p[0] + "," + p[1]);
                return false;
            }
        }
        return true;
    }

    /** Row edges must stay inside the scrollbar arc (r=108, ±48°). */
    private static boolean rowsClearScrollbar(float listTopDp, int ignored) {
        return clearScrollbar(listTopDp, false);
    }

    private static boolean routeRowsClearScrollbar(float listTopDp) {
        return clearScrollbar(listTopDp, true);
    }

    private static boolean clearScrollbar(float listTopDp, boolean routes) {
        int count = routes ? RouteList.VISIBLE : FocalList.VISIBLE;
        for (int row = 0; row < count; row++) {
            float top = routes ? RouteList.rowTopDp(listTopDp, row) : FocalList.rowTopDp(listTopDp, row);
            float h = routes ? RouteList.rowHeightDp(row) : FocalList.rowHeightDp(row);
            float far = Math.max(Math.abs(top - 116.5f), Math.abs(top + h - 116.5f));
            if (far > 84f) continue; // вне дуги шкалы
            float half = FocalList.widthForRow(top, h, 112f, 116.5f, 10f, 214f) / 2f;
            float sb = (float) Math.sqrt(108f * 108f - far * far) - 2.2f;
            if (half > sb) {
                System.out.println("    row " + row + " touches scrollbar: " + half + " > " + sb);
                return false;
            }
        }
        return true;
    }

    private static boolean hasRoundRect(UiFrame f) {
        for (UiFrame.Op op : f.ops) if (op.type == UiFrame.ROUND_RECT) return true;
        return false;
    }

    private static int countType(UiFrame f, int type) {
        int n = 0;
        for (UiFrame.Op op : f.ops) if (op.type == type) n++;
        return n;
    }

    private static boolean hasText(UiFrame f, String sub) {
        for (UiFrame.Op op : f.ops) {
            if (op.type == UiFrame.TEXT && op.text.contains(sub)) return true;
        }
        return false;
    }

    private static boolean textInsideCircle(UiFrame f, Metrics m, float r) {
        float cx = 233f, cy = 233f;
        for (UiFrame.Op op : f.ops) {
            if (op.type != UiFrame.TEXT) continue;
            float w = m.width(op.text, op.size, op.bold);
            float x0 = op.align == UiFrame.ALIGN_CENTER ? op.x - w / 2
                    : (op.align == UiFrame.ALIGN_RIGHT ? op.x - w : op.x);
            float x1 = x0 + w;
            float y0 = op.y - op.size * 0.85f;
            float y1 = op.y + op.size * 0.2f;
            float[][] corners = {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
            for (float[] p : corners) {
                float dx = p[0] - cx, dy = p[1] - cy;
                if (Math.sqrt(dx * dx + dy * dy) > r) {
                    System.out.println("    outside: '" + op.text + "' at " + p[0] + "," + p[1]);
                    return false;
                }
            }
        }
        return true;
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }

    /** Deterministic stand-in for Paint.measureText. */
    static final class FakeMetrics implements Metrics {
        @Override
        public float width(String text, float sizePx, boolean bold) {
            float k = bold ? 0.58f : 0.53f;
            return text == null ? 0 : text.length() * sizePx * k;
        }
    }
}
