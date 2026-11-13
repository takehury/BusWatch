package ru.krsk.buswatch;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders the real UiFrame produced by WatchUi with Java2D and real font
 * metrics, so the round layout can be reviewed on the build machine.
 */
public final class Preview {

    private static final int SIZE = 466;

    public static void main(String[] args) throws Exception {
        String fixtures = args.length > 0 ? args[0] : "tests/fixtures";
        String outDir = args.length > 1 ? args[1] : "tests/preview";
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        new File(outDir).mkdirs();

        renderScale = scale;
        Catalog catalog = Catalog.parse(read(fixtures + "/routes.json"),
                read(fixtures + "/stops_place3.json"), read(fixtures + "/route_stops.json"));
        WatchUi ui = new WatchUi(2f, 2f, SIZE, SIZE);

        // 01 route picker
        WatchState routes = new WatchState();
        routes.city = new City(3, "krasnoyarsk", "Красноярск", 56.01, 92.85, 160, 999);
        routes.setRoutes(catalog.groundRoutes());
        routes.status = WatchState.STATUS_READY;
        routes.gpsFix = true;
        routes.accuracyM = 9;
        routes.routeSel.move(2);
        preview(ui, routes, outDir + "/01-routes.png");

        // 02 stop board
        RouteInfo route = catalog.route(534);
        RouteStops rs = catalog.routeStops(534);
        List<Vehicle> live = new ArrayList<>();
        for (Vehicle v : FeedParser.parse(read(fixtures + "/bus_534.json"), route)) {
            if (!v.sleeping) live.add(v);
        }
        long now = 1794275200L;
        Board board = Board.build(rs.dir0, live, 0, Geo.CITY_LAT, Geo.CITY_LON, now, 300);
        WatchState stops = new WatchState();
        stops.setRoutes(catalog.groundRoutes());
        stops.selectRoute(route);
        stops.openRoute(route, 0);
        stops.setBoard(rs, board, board.userStopIndex, live.size());
        stops.status = WatchState.STATUS_READY;
        stops.gpsFix = true;
        preview(ui, stops, outDir + "/02-stops.png");

        // 03 direction 1
        Board board1 = Board.build(rs.dir1, live, 1, Geo.CITY_LAT, Geo.CITY_LON, now, 300);
        WatchState stops1 = new WatchState();
        stops1.setRoutes(catalog.groundRoutes());
        stops1.selectRoute(route);
        stops1.openRoute(route, 1);
        stops1.setBoard(rs, board1, board1.userStopIndex, live.size());
        stops1.status = WatchState.STATUS_READY;
        stops1.gpsFix = true;
        preview(ui, stops1, outDir + "/03-stops-dir1.png");

        // 04 stop menu (with manual + armed alert shown)
        WatchState menu = new WatchState();
        menu.setRoutes(catalog.groundRoutes());
        menu.selectRoute(route);
        menu.openRoute(route, 0);
        menu.setBoard(rs, board, board.userStopIndex, live.size());
        menu.status = WatchState.STATUS_READY;
        menu.armAlert(board.userStopIndex, rs.dir0.get(board.userStopIndex).name);
        menu.openStopMenu();
        preview(ui, menu, outDir + "/04-stop-menu.png");

        // 05 settings
        WatchState settings = new WatchState();
        settings.setRoutes(catalog.groundRoutes());
        settings.status = WatchState.STATUS_READY;
        settings.openSettings();
        preview(ui, settings, outDir + "/05-settings.png");

        // 09 city picker
        WatchState cityScreen = new WatchState();
        cityScreen.status = WatchState.STATUS_READY;
        cityScreen.cities.add(new City(3, "krasnoyarsk", "Красноярск", 56.01, 92.85, 160, 999));
        cityScreen.cities.add(new City(23, "novosibirsk", "Новосибирск", 55.03, 82.92, 251, 1282));
        cityScreen.cities.add(new City(17, "moscow", "Москва", 55.75, 37.61, 1934, 10212));
        cityScreen.cities.add(new City(5, "spb", "Санкт-Петербург", 59.91, 30.33, 1273, 4669));
        cityScreen.city = cityScreen.cities.get(0);
        cityScreen.cityAuto = false;
        cityScreen.openCities();
        preview(ui, cityScreen, outDir + "/09-cities.png");

        // 12: first item centred (padding rows above)
        WatchState first = new WatchState();
        first.city = new City(3, "krasnoyarsk", "Красноярск", 56.01, 92.85, 160, 999);
        first.setRoutes(catalog.groundRoutes());
        first.status = WatchState.STATUS_READY;
        first.gpsFix = true;
        first.routeSel.select(0);
        preview(ui, first, outDir + "/12-first-item.png");

        // 10/11: same design scaled to 360 and 480 px watches
        BufferedImage big = render(ui, routes, 466);
        writeScaled(big, 360, outDir + "/10-size-360.png");
        writeScaled(big, 480, outDir + "/11-size-480.png");

        WatchState loading = new WatchState();
        loading.status = WatchState.STATUS_LOADING;
        preview(ui, loading, outDir + "/06-loading.png");

        WatchState error = new WatchState();
        error.status = WatchState.STATUS_ERROR;
        error.message = "Нет связи с сервером";
        preview(ui, error, outDir + "/07-error.png");

        WatchState denied = new WatchState();
        denied.status = WatchState.STATUS_NO_PERMISSION;
        denied.message = "Нет доступа к геопозиции";
        preview(ui, denied, outDir + "/08-denied.png");

        renderSpb(fixtures, outDir);
        renderFavourites(catalog, outDir);
        System.out.println("previews written to " + outDir);
    }

    private static int renderScale = 1;

    private static BufferedImage render(WatchUi ui, WatchState state, int size) throws Exception {
        int px = size * renderScale;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(renderScale, renderScale);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        AwtMetrics metrics = new AwtMetrics(g);
        replay(g, ui.build(state, metrics), metrics);
        g.dispose();
        return img;
    }

    private static void writeScaled(BufferedImage src, int size, String out) throws Exception {
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        ImageIO.write(dst, "png", new File(out));
        System.out.println("== " + new File(out).getName() + " (scaled " + size + "px)");
    }


    /** Screens for the favourites list. */
    private static void renderFavourites(Catalog catalog, String outDir) throws Exception {
        WatchUi ui = new WatchUi(2f, 2f, SIZE, SIZE);
        WatchState s = new WatchState();
        s.status = WatchState.STATUS_READY;
        s.city = new City(3, "krasnoyarsk", "Красноярск", 56.01, 92.85, 160, 999);
        s.setRoutes(catalog.groundRoutes());
        List<RouteInfo> list = s.routes;
        if (list.size() > 2) {
            s.favs.add(list.get(1).id);
            s.favs.add(list.get(4).id);
        }
        s.openFavourites();
        s.gpsFix = true;
        preview(ui, s, outDir + "/19-favourites.png");

        // empty favourites: faded heart + hint
        WatchState empty = new WatchState();
        empty.status = WatchState.STATUS_READY;
        empty.city = s.city;
        empty.setRoutes(catalog.groundRoutes());
        empty.openFavourites();
        empty.gpsFix = true;
        preview(ui, empty, outDir + "/27-favourites-empty.png");

        // the main route list with a favourite marked
        WatchState main = new WatchState();
        main.status = WatchState.STATUS_READY;
        main.city = new City(3, "krasnoyarsk", "Красноярск", 56.01, 92.85, 160, 999);
        main.setRoutes(catalog.groundRoutes());
        if (main.routes.size() > 3) main.favs.add(main.routes.get(2).id);
        main.routeSel.select(2);
        preview(ui, main, outDir + "/23-routes-fav.png");

        // manual position: the heart must turn yellow
        s.locationMode = WatchState.LOC_MANUAL;
        s.manualLat = 56.0;
        s.manualLon = 92.9;
        s.manualStopName = "Остановка";
        preview(ui, s, outDir + "/22-favourites-manual.png");
        s.locationMode = WatchState.LOC_AUTO;

        s.openFavPicker();
        preview(ui, s, outDir + "/20-fav-picker.png");
        s.closeMenu();

        s.openSettings();
        preview(ui, s, outDir + "/21-settings-fav.png");

        // the starting-screen chooser
        s.startScreenFav = true;
        s.openStartScreen();
        preview(ui, s, outDir + "/25-start-screen.png");

        // manual position: the GPS dot turns yellow
        s.locationMode = WatchState.LOC_MANUAL;
        s.manualLat = 56.0;
        s.manualLon = 92.9;
        s.manualStopName = "Остановка";
        preview(ui, s, outDir + "/26-start-screen-manual.png");
        s.locationMode = WatchState.LOC_AUTO;
        s.closeMenu();

        // the favourites list with the selection in the middle
        s.favSel.select(0);
        s.refreshFavList();
        s.favSel.select(0);
        preview(ui, s, outDir + "/24-fav-selected.png");
    }

    /** Screens for Saint Petersburg, used for marketing shots. */
    private static void renderSpb(String fixtures, String outDir) throws Exception {
        String root = "app/assets/";
        List<City> cities = Cities.parse(read(root + "cities.json"));
        City spb = Cities.byId(cities, 5);
        Catalog cat = Catalog.parse(read(root + "cities/5_routes.json"),
                read(root + "cities/5_stops.json"), null);
        RouteInfo route = cat.route(195);
        RouteStops rs = RoutePageParser.parse(195, read(fixtures + "/spb_page.html"));
        List<Vehicle> live = new ArrayList<>();
        for (Vehicle v : FeedParser.parse(read(fixtures + "/spb_feed.json"), route)) {
            if (!v.sleeping) live.add(v);
        }
        // "now" from the feed itself, otherwise every bus looks stale
        long now = 0;
        for (Vehicle v : live) now = Math.max(now, v.timestampSec);
        now += 30;

        List<StopInfo> stops = rs.direction(0);
        // put the user on a stop that actually has a bus, or at least a forecast
        int target = 0;
        Board probe = Board.build(stops, live, 0, stops.get(0).lat, stops.get(0).lon, now, 300);
        for (Board.Row row : probe.rows) {
            if (row.hasBusHere()) {
                target = row.index;
                break;
            }
        }
        if (target == 0) {
            for (Board.Row row : probe.rows) {
                if (row.etaMinutes > 0) {
                    target = row.index;
                    break;
                }
            }
        }
        StopInfo anchor = stops.get(Math.min(target, stops.size() - 1));
        Board board = Board.build(stops, live, 0, anchor.lat, anchor.lon, now, 300);

        WatchUi ui = new WatchUi(2f, 2f, SIZE, SIZE);
        WatchState s = new WatchState();
        s.status = WatchState.STATUS_READY;
        s.cities.addAll(cities);
        s.city = spb;
        s.cityAuto = false;
        s.setRoutes(cat.groundRoutes());
        s.routeSel.select(Math.min(40, s.routes.size() - 1));
        preview(ui, s, outDir + "/13-spb-routes.png");

        s.selectRoute(route);
        s.openRoute(route, 0);
        s.setBoard(rs, board, board.userStopIndex, 3);
        if (board.userStopIndex >= 0) s.stopSel.select(board.userStopIndex);
        preview(ui, s, outDir + "/14-spb-stops.png");

        s.openStopMenu();
        preview(ui, s, outDir + "/15-spb-stop-menu.png");
        s.closeMenu();

        s.openSettings();
        preview(ui, s, outDir + "/16-spb-settings.png");
        s.closeMenu();

        s.openCities();
        preview(ui, s, outDir + "/17-spb-cities.png");
        s.closeMenu();

        s.screen = WatchState.SCREEN_ROUTES;
        s.status = WatchState.STATUS_ERROR;
        s.message = "Нет связи с сервером";
        preview(ui, s, outDir + "/18-spb-error.png");
    }

    private static void preview(WatchUi ui, WatchState state, String out) throws Exception {
        int px = SIZE * renderScale;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(renderScale, renderScale);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, SIZE, SIZE);

        AwtMetrics metrics = new AwtMetrics(g);
        UiFrame frame = ui.build(state, metrics);
        replay(g, frame, metrics);

        g.dispose();
        ImageIO.write(img, "png", new File(out));
        String name = new File(out).getName();
        System.out.println("== " + name + " ops=" + frame.ops.size()
                + " inside=" + allInside(frame, metrics));
        if (name.startsWith("01") || name.startsWith("02")) ascii(img);
    }

    private static void replay(Graphics2D g, UiFrame f, AwtMetrics m) {
        for (UiFrame.Op op : f.ops) {
            Color color = new Color(op.color, true);
            switch (op.type) {
                case UiFrame.RECT:
                    g.setColor(color);
                    g.fillRect((int) op.x, (int) op.y, (int) op.w, (int) op.h);
                    break;
                case UiFrame.CIRCLE:
                    g.setColor(color);
                    g.fill(new Ellipse2D.Float(op.x - op.w, op.y - op.w, op.w * 2, op.w * 2));
                    break;
                case UiFrame.ROUND_RECT:
                    g.setColor(color);
                    g.fill(new RoundRectangle2D.Float(op.x, op.y, op.w, op.h, op.r * 2, op.r * 2));
                    break;
                case UiFrame.RING:
                    g.setColor(color);
                    g.setStroke(new BasicStroke(op.stroke));
                    g.draw(new Ellipse2D.Float(op.x - op.w, op.y - op.w, op.w * 2, op.w * 2));
                    break;
                case UiFrame.ARC:
                    g.setColor(color);
                    g.setStroke(new BasicStroke(op.stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Arc2D.Float(op.x - op.w, op.y - op.w, op.w * 2, op.w * 2,
                            op.start, op.sweep, Arc2D.OPEN));
                    break;
                case UiFrame.LINE:
                    g.setColor(color);
                    g.setStroke(new BasicStroke(op.stroke));
                    g.drawLine((int) op.x, (int) op.y, (int) op.x2, (int) op.y2);
                    break;
                case UiFrame.RADIAL:
                    g.setPaint(new RadialGradientPaint(new Point2D.Float(op.x, op.y), op.w,
                            new float[]{0f, 1f}, new Color[]{new Color(op.color, true), new Color(op.color2, true)}));
                    g.fillRect(0, 0, SIZE, SIZE);
                    break;
                case UiFrame.POLYGON: {
                    if (op.pts == null || op.pts.length < 6) break;
                    java.awt.geom.Path2D.Float poly = new java.awt.geom.Path2D.Float();
                    poly.moveTo(op.pts[0], op.pts[1]);
                    for (int i = 2; i + 1 < op.pts.length; i += 2) {
                        poly.lineTo(op.pts[i], op.pts[i + 1]);
                    }
                    poly.closePath();
                    g.setColor(color);
                    g.fill(poly);
                    break;
                }
                case UiFrame.HEART: {
                    float k = op.w / 9f;
                    float a = UiFrame.HEART_ASPECT;
                    java.awt.geom.Path2D.Float heart = new java.awt.geom.Path2D.Float();
                    heart.moveTo(op.x, op.y + 9f * k);
                    heart.curveTo(op.x - 9f * a * k, op.y + 2.5f * k,
                            op.x - 9f * a * k, op.y - 9f * k, op.x, op.y - 3f * k);
                    heart.curveTo(op.x + 9f * a * k, op.y - 9f * k,
                            op.x + 9f * a * k, op.y + 2.5f * k, op.x, op.y + 9f * k);
                    heart.closePath();
                    g.setColor(color);
                    g.fill(heart);
                    break;
                }
                case UiFrame.TRIANGLE: {
                    g.setColor(color);
                    g.fillPolygon(new int[]{(int) op.x, (int) op.x2, (int) op.w},
                            new int[]{(int) op.y, (int) op.y2, (int) op.h}, 3);
                    break;
                }
                case UiFrame.TEXT:
                    Font font = new Font("SansSerif", op.bold ? Font.BOLD : Font.PLAIN, 12)
                            .deriveFont(op.size);
                    g.setFont(font);
                    g.setColor(color);
                    float w = m.width(op.text, op.size, op.bold);
                    float x = op.x;
                    if (op.align == UiFrame.ALIGN_CENTER) x -= w / 2f;
                    else if (op.align == UiFrame.ALIGN_RIGHT) x -= w;
                    g.drawString(op.text, x, op.y);
                    break;
                case UiFrame.CLIP_CIRCLE:
                    g.setClip(new Ellipse2D.Float(op.x - op.w, op.y - op.w, op.w * 2, op.w * 2));
                    break;
                case UiFrame.RESTORE:
                    g.setClip(null);
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean allInside(UiFrame f, AwtMetrics m) {
        float cx = 233, cy = 233, r = 224;
        for (UiFrame.Op op : f.ops) {
            if (op.type != UiFrame.TEXT) continue;
            float w = m.width(op.text, op.size, op.bold);
            float x0 = op.align == UiFrame.ALIGN_CENTER ? op.x - w / 2
                    : (op.align == UiFrame.ALIGN_RIGHT ? op.x - w : op.x);
            float x1 = x0 + w;
            float y0 = op.y - op.size * 0.85f;
            float y1 = op.y + op.size * 0.2f;
            float[][] pts = {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
            for (float[] p : pts) {
                float dx = p[0] - cx, dy = p[1] - cy;
                if (Math.sqrt(dx * dx + dy * dy) > r) {
                    System.out.println("   clip risk: '" + op.text + "' corner " + (int) p[0] + "," + (int) p[1]);
                    return false;
                }
            }
        }
        return true;
    }

    private static void ascii(BufferedImage img) {
        int cols = 58;
        int rows = 29;
        String ramp = " .:-=+*#%@";
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x0 = c * SIZE / cols, x1 = (c + 1) * SIZE / cols;
                int y0 = r * SIZE / rows, y1 = (r + 1) * SIZE / rows;
                double sum = 0;
                int n = 0;
                for (int y = y0; y < y1; y += 2) {
                    for (int x = x0; x < x1; x += 2) {
                        int argb = img.getRGB(x, y);
                        int a = (argb >>> 24);
                        if (a == 0) continue;
                        int lum = ((argb >> 16) & 255) * 30 + ((argb >> 8) & 255) * 59 + (argb & 255) * 11;
                        sum += lum / 100.0;
                        n++;
                    }
                }
                double v = n == 0 ? 0 : sum / n;
                int idx = (int) Math.min(ramp.length() - 1, v / 255.0 * ramp.length());
                sb.append(ramp.charAt(idx));
            }
            sb.append('\n');
        }
        System.out.print(sb);
    }

    private static String read(String p) throws Exception {
        return new String(Files.readAllBytes(Paths.get(p)), StandardCharsets.UTF_8);
    }

    private static final class AwtMetrics implements Metrics {
        private final Graphics2D g;

        AwtMetrics(Graphics2D g) {
            this.g = g;
        }

        @Override
        public float width(String text, float sizePx, boolean bold) {
            Font f = new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 12).deriveFont(sizePx);
            FontMetrics fm = g.getFontMetrics(f);
            return fm.stringWidth(text == null ? "" : text);
        }
    }
}
