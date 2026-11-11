package ru.krsk.buswatch;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the bundled multi-city catalogue and the live feed.
 *
 * Every city ships routes + stops; ordered route stops are bundled only for
 * Krasnoyarsk, for other cities they are fetched from the route page once and
 * cached for the session.
 */
public final class BusRepository {

    private static final String[] FEED_HOSTS = {
            "https://busti.me/ajax/bus/?bus_id=",
            "https://en.busti.me/ajax/bus/?bus_id="
    };
    // ru.busti.me blocks the JSSE TLS handshake used on Android/desktop Java,
    // the mirrors serve the very same route pages and work fine.
    private static final String[] PAGE_HOSTS = {
            "https://ru.busti.me/",
            "https://en.busti.me/",
            "https://busti.me/"
    };

    private final Context context;
    private final Map<Integer, Catalog> catalogs = new HashMap<>();
    private final Map<Integer, RouteStops> routeStopsCache = new HashMap<>();

    public BusRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<City> loadCities() throws IOException {
        return Cities.parse(readAsset("cities.json"));
    }

    /** Loads (and caches) the catalogue of one city. */
    public Catalog loadCity(City city) throws IOException {
        Catalog cached = catalogs.get(city.id);
        if (cached != null) return cached;
        String routes = readAsset("cities/" + city.id + "_routes.json");
        String stops = readAsset("cities/" + city.id + "_stops.json");
        String routeStops = readOptionalAsset("cities/" + city.id + "_route_stops.json");
        Catalog c = Catalog.parse(routes, stops, routeStops);
        catalogs.put(city.id, c);
        return c;
    }

    /**
     * Ordered stops of a route: bundled data when available, otherwise the
     * route page is fetched and parsed (cached afterwards).
     */
    public RouteStops routeStops(City city, RouteInfo route) throws IOException {
        Catalog catalog = loadCity(city);
        RouteStops bundled = catalog.routeStops(route.id);
        if (bundled.hasData()) return bundled;

        RouteStops cached = routeStopsCache.get(route.id);
        if (cached != null && cached.hasData()) return cached;

        String slug = route.slug == null || route.slug.isEmpty()
                ? "bus-" + route.name.toLowerCase() : route.slug;
        IOException last = null;
        for (String host : PAGE_HOSTS) {
            try {
                int readMs = host.contains("ru.busti.me") ? 8000 : 12000;
                String html = Http.get(host + city.slug + "/" + slug + "/", 6000, readMs);
                RouteStops parsed = RoutePageParser.parse(route.id, html);
                if (parsed.hasData()) {
                    routeStopsCache.put(route.id, parsed);
                    return parsed;
                }
            } catch (IOException ex) {
                last = ex;
            }
        }
        throw last == null ? new IOException("route page empty") : last;
    }

    /** Live vehicles of one route. */
    public List<Vehicle> fetchRoute(RouteInfo route) throws IOException {
        IOException last = null;
        for (String host : FEED_HOSTS) {
            try {
                String json = Http.get(host + route.id, 5000, 8000);
                List<Vehicle> parsed = FeedParser.parse(json, route);
                if (!parsed.isEmpty()) return parsed;
                return parsed;
            } catch (IOException ex) {
                last = ex;
            }
        }
        throw last == null ? new IOException("feed unavailable") : last;
    }

    private String readAsset(String name) throws IOException {
        InputStream in = context.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        byte[] buf = new byte[32768];
        int n;
        try {
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private String readOptionalAsset(String name) {
        try {
            return readAsset(name);
        } catch (IOException ex) {
            return null;
        }
    }

    public void close() {
        catalogs.clear();
        routeStopsCache.clear();
    }

    public List<City> unmodifiable(List<City> cities) {
        return Collections.unmodifiableList(cities);
    }
}
