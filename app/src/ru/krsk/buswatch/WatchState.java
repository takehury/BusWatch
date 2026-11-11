package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything the round UI needs to draw one frame. */
public final class WatchState {

    public static final int SCREEN_ROUTES = 0;
    public static final int SCREEN_STOPS = 1;
    public static final int SCREEN_STOP_MENU = 2;
    public static final int SCREEN_SETTINGS = 3;
    public static final int SCREEN_CITIES = 4;

    public static final int STATUS_LOADING = 0;
    public static final int STATUS_READY = 1;
    public static final int STATUS_EMPTY = 2;
    public static final int STATUS_ERROR = 3;
    public static final int STATUS_NO_PERMISSION = 4;

    public static final int LOC_AUTO = 0;
    public static final int LOC_MANUAL = 1;

    public int screen = SCREEN_ROUTES;
    public int status = STATUS_LOADING;
    public String message = "";

    // ---- route picker -------------------------------------------------
    public final List<RouteInfo> routes = new ArrayList<>();
    public final Selection routeSel = new Selection();
    public RouteInfo selectedRoute;

    // ---- stops screen -------------------------------------------------
    public int dir = 0;                          // current direction bit
    public final Selection stopSel = new Selection();
    public RouteStops routeStops;
    public Board board;
    public int userStopIndex = -1;
    public int busesHereCount;

    // ---- stop menu / settings -----------------------------------------
    public final Selection menuSel = new Selection();
    public int menuParent = SCREEN_ROUTES;

    // ---- cities --------------------------------------------------------
    public final List<City> cities = new ArrayList<>();
    public final Selection citySel = new Selection();
    public City city;
    public boolean cityAuto = true;

    // ---- location -----------------------------------------------------
    public int locationMode = LOC_AUTO;
    public boolean autoTrack = true;
    public double lat = Geo.CITY_LAT;
    public double lon = Geo.CITY_LON;
    public int accuracyM = -1;
    public boolean hasFix;
    public boolean gpsFix;
    public boolean gpsFallback;

    // manual location (chosen stop)
    public double manualLat = Double.NaN;
    public double manualLon = Double.NaN;
    public String manualStopName = "";

    // ---- bus arrival alert ---------------------------------------------
    public boolean alertsEnabled = true;
    public boolean alertArmed;
    public int alertRouteId;
    public int alertDir;
    public int alertStopIndex = -1;
    public String alertStopName = "";

    public boolean refreshing;
    public long lastUpdate;

    // ------------------------------------------------------------------ routes

    public void setRoutes(List<RouteInfo> list) {
        routes.clear();
        if (list != null) routes.addAll(list);
        List<String> keys = new ArrayList<>();
        for (RouteInfo r : routes) keys.add(String.valueOf(r.id));
        routeSel.setKeys(keys);
        selectedRoute = null;
        routeStops = null;
        board = null;
        // a catalogue reload must not yank the user out of settings or the
        // city picker; only route-specific screens fall back to the list
        if (screen == SCREEN_STOPS || screen == SCREEN_STOP_MENU) {
            screen = SCREEN_ROUTES;
        }
    }

    public void selectRoute(RouteInfo r) {
        selectedRoute = r;
        if (r != null) {
            int idx = routes.indexOf(r);
            if (idx >= 0) routeSel.select(idx);
        }
    }

    public RouteInfo currentRoute() {
        return selectedRoute != null ? selectedRoute
                : (routeSel.isEmpty() ? null : routeById(routeSel.selectedKey()));
    }

    public RouteInfo routeAt(int index) {
        if (index < 0 || index >= routes.size()) return null;
        return routes.get(index);
    }

    public RouteInfo routeById(String idStr) {
        if (idStr == null) return null;
        try {
            int id = Integer.parseInt(idStr);
            for (RouteInfo r : routes) if (r.id == id) return r;
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    /** Opens the stops screen for the given route and direction. */
    public void openRoute(RouteInfo r, int direction) {
        selectedRoute = r;
        dir = direction < 0 ? 0 : direction;
        screen = SCREEN_STOPS;
        routeStops = null;
        board = null;
        stopSel.setKeys(Collections.<String>emptyList());
        stopSel.select(0);
        userStopIndex = -1;
        busesHereCount = 0;
    }

    public void setBoard(RouteStops rs, Board b, int userStopIndex, int busesHere) {
        this.routeStops = rs;
        this.board = b;
        this.userStopIndex = userStopIndex;
        this.busesHereCount = busesHere;
        List<String> keys = new ArrayList<>();
        if (b != null) {
            for (Board.Row row : b.rows) keys.add(String.valueOf(row.index));
        }
        // Keep the row the user scrolled to across live refreshes; only the
        // first board (or one after a route/direction change) jumps to "you".
        boolean firstFill = stopSel.isEmpty();
        stopSel.setKeys(keys);
        if (firstFill && userStopIndex >= 0 && userStopIndex < keys.size()) {
            stopSel.select(userStopIndex);
        } else if (stopSel.isEmpty() && !keys.isEmpty()) {
            stopSel.select(0);
        }
    }

    public int currentDirStopsCount() {
        return routeStops == null ? 0 : routeStops.direction(dir).size();
    }

    public boolean canToggleDirection() {
        if (selectedRoute == null || routeStops == null) return false;
        return !routeStops.dir0.isEmpty() && !routeStops.dir1.isEmpty();
    }

    // ------------------------------------------------------------------ menus

    public void openStopMenu() {
        screen = SCREEN_STOP_MENU;
        menuParent = SCREEN_STOPS;
        setMenuSize(3);
    }

    public void openSettings() {
        screen = SCREEN_SETTINGS;
        menuParent = SCREEN_ROUTES;
        setMenuSize(4);
    }

    /** City picker: "Авто" + all supported cities. */
    public void openCities() {
        screen = SCREEN_CITIES;
        menuParent = SCREEN_SETTINGS;
        List<String> keys = new ArrayList<>();
        keys.add("auto");
        for (City c : cities) keys.add(String.valueOf(c.id));
        citySel.setKeys(keys);
        int selected = 0;
        if (!cityAuto && city != null) {
            int idx = cities.indexOf(city);
            if (idx >= 0) selected = idx + 1;
        }
        citySel.select(selected);
    }

    public int cityOptionsCount() {
        return cities.size() + 1;
    }

    public String cityOptionName(int index) {
        if (index <= 0) return "Авто (по GPS)";
        City c = index - 1 < cities.size() ? cities.get(index - 1) : null;
        return c == null ? "?" : c.name;
    }

    public void closeMenu() {
        int target = menuParent;
        screen = target;
        // reset the parent chain: closing the city picker returns to settings,
        // closing settings must then return to the route list (not loop back)
        menuParent = SCREEN_ROUTES;
    }

    private void setMenuSize(int n) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) keys.add(String.valueOf(i));
        menuSel.setKeys(keys);
        menuSel.select(0);
    }

    // ---------------------------------------------------------------- location

    public boolean manualActive() {
        return locationMode == LOC_MANUAL
                && !Double.isNaN(manualLat) && !Double.isNaN(manualLon);
    }

    public double effLat() {
        return manualActive() ? manualLat : lat;
    }

    public double effLon() {
        return manualActive() ? manualLon : lon;
    }

    public void setManualLocation(String name, double la, double lo) {
        manualStopName = name == null ? "" : name;
        manualLat = la;
        manualLon = lo;
        locationMode = LOC_MANUAL;
    }

    public void clearManualLocation() {
        manualStopName = "";
        manualLat = Double.NaN;
        manualLon = Double.NaN;
        locationMode = LOC_AUTO;
    }

    /** Short label for the location source shown in the header. */
    public String locLabel() {
        if (manualActive()) return "РУЧ";
        if (gpsFix) return "GPS";
        if (gpsFallback) return "ЦЕНТР";
        if (hasFix) return "СЕТЬ";
        return "—";
    }

    // ------------------------------------------------------------------ alert

    public void armAlert(int stopIndex, String stopName) {
        if (selectedRoute == null) return;
        alertArmed = true;
        alertRouteId = selectedRoute.id;
        alertDir = dir;
        alertStopIndex = stopIndex;
        alertStopName = stopName == null ? "" : stopName;
    }

    public void disarmAlert() {
        alertArmed = false;
        alertRouteId = 0;
        alertDir = 0;
        alertStopIndex = -1;
        alertStopName = "";
    }

    public boolean isAlertArmedAt(int stopIndex) {
        return alertArmed && alertStopIndex == stopIndex
                && selectedRoute != null && alertRouteId == selectedRoute.id
                && alertDir == dir;
    }

    /** StopInfo currently selected on the stops screen (for the stop menu). */
    public Board.Row selectedStopRow() {
        if (board == null) return null;
        int idx = stopSel.selectedIndex();
        if (idx < 0 || idx >= board.rows.size()) return null;
        return board.rows.get(idx);
    }
}
