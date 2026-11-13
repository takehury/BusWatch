package ru.krsk.buswatch;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point. Route picker -> per-route stop board with live bus positions.
 * Owns permissions, location, the multi-city catalogue and the arrival alert.
 */
public final class BusWatchActivity extends Activity implements WatchView.Listener {

    private static final int REQ_LOCATION = 7;
    /** Late feed: buses move fast, so refresh often while the board is open. */
    private static final long REFRESH_ACTIVE_MS = 3000L;
    private static final long REFRESH_IDLE_MS = 15000L;
    private static final int DEFAULT_CITY = 3; // Красноярск

    private final WatchState state = new WatchState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WatchView view;
    private BusRepository repo;
    private LocationHelper location;
    private Prefs prefs;
    private ExecutorService worker;
    private boolean resumed;
    private boolean loading;
    private boolean catalogReady;
    private boolean gotFix;
    private boolean cityLoading;
    private List<City> cities = new ArrayList<>();
    private List<RouteInfo> catalogRoutes = new ArrayList<>();
    private City city;
    private boolean cityAuto = true;

    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            refreshBoard(false);
        }
    };

    private final Runnable fallbackTick = new Runnable() {
        @Override
        public void run() {
            if (!gotFix && state.autoTrack && !state.manualActive()) {
                state.hasFix = false;
                state.gpsFix = false;
                state.gpsFallback = true;
                state.lat = city == null ? Geo.CITY_LAT : city.lat;
                state.lon = city == null ? Geo.CITY_LON : city.lon;
                rebuildBoardForCurrentRoute();
                view.invalidate();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        view = new WatchView(this);
        view.setListener(this);
        view.post(new Runnable() {
            @Override
            public void run() {
                view.applyGestureExclusion();
            }
        });
        view.setState(state);
        setContentView(view);

        worker = Executors.newSingleThreadExecutor();
        repo = new BusRepository(this);
        prefs = new Prefs(this);
        applyPrefsToState();

        location = new LocationHelper(this, new LocationHelper.Listener() {
            @Override
            public void onFix(double lat, double lon, int accuracyM, boolean fromGps) {
                state.lat = lat;
                state.lon = lon;
                state.accuracyM = accuracyM;
                state.hasFix = true;
                state.gpsFix = fromGps;
                state.gpsFallback = false;
                gotFix = true;
                handler.removeCallbacks(fallbackTick);
                maybeAutoSelectCity(lat, lon);
                if (state.screen == WatchState.SCREEN_STOPS && state.selectedRoute != null) {
                    rebuildBoardForCurrentRoute();
                }
                view.invalidate();
            }
        });

        state.status = WatchState.STATUS_LOADING;
        loadInitial();
    }

    /** Loads the city list and the saved (or default) city catalogue. */
    private void loadInitial() {
        worker.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<City> loaded = repo.loadCities();
                    int savedId = prefs.cityId();
                    City chosen = savedId != 0 ? Cities.byId(loaded, savedId) : null;
                    final boolean auto = chosen == null;
                    if (chosen == null) chosen = Cities.byId(loaded, DEFAULT_CITY);
                    if (chosen == null && !loaded.isEmpty()) chosen = loaded.get(0);
                    if (chosen == null) throw new IllegalStateException("no cities");
                    final City target = chosen;
                    final Catalog catalog = repo.loadCity(target);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            cities = loaded;
                            cityAuto = auto;
                            city = target;
                            state.cities.clear();
                            state.cities.addAll(loaded);
                            state.city = target;
                            state.cityAuto = auto;
                            catalogRoutes = catalog.groundRoutes();
                            applyRouteFilter();
                            state.status = state.routes.isEmpty()
                                    ? WatchState.STATUS_EMPTY : WatchState.STATUS_READY;
                            catalogReady = true;
                            // the start screen: all routes or the favourites list
                            if (state.startScreenFav && !state.favs.isEmpty()) {
                                state.openFavourites();
                            }
                            view.invalidate();
                        }
                    });
                } catch (final Exception ex) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            catalogReady = true;
                            state.status = WatchState.STATUS_ERROR;
                            state.message = "Ошибка каталога";
                            view.invalidate();
                        }
                    });
                }
            }
        });
    }

    /** Switches the active city in the background (used by the picker and GPS). */
    private void switchCity(final City target, final boolean auto) {
        if (target == null || cityLoading) return;
        if (city != null && city.id == target.id) {
            cityAuto = auto;
            state.cityAuto = auto;
            return;
        }
        cityLoading = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                // keep the current list on screen; only show the small spinner
                state.refreshing = true;
                view.invalidate();
            }
        });
        worker.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Catalog catalog = repo.loadCity(target);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            cityLoading = false;
                            city = target;
                            cityAuto = auto;
                            state.city = target;
                            state.cityAuto = auto;
                            state.refreshing = false;
                            catalogRoutes = catalog.groundRoutes();
                            applyRouteFilter();
                            state.status = state.routes.isEmpty()
                                    ? WatchState.STATUS_EMPTY : WatchState.STATUS_READY;
                            catalogReady = true;
                            if (state.startScreenFav && !state.favs.isEmpty()) {
                                state.openFavourites();
                            }
                            view.invalidate();
                            schedule();
                        }
                    });
                } catch (final Exception ex) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            cityLoading = false;
                            state.refreshing = false;
                            state.status = state.routes.isEmpty()
                                    ? WatchState.STATUS_ERROR : state.status;
                            state.message = "Ошибка каталога";
                            view.invalidate();
                        }
                    });
                }
            }
        });
    }

    private void maybeAutoSelectCity(double lat, double lon) {
        if (!cityAuto || cities.isEmpty() || cityLoading) return;
        City near = Cities.nearest(cities, lat, lon, 250);
        if (near != null && (city == null || near.id != city.id)) {
            switchCity(near, true);
        }
    }

    private void applyPrefsToState() {
        state.autoTrack = prefs.autoTrack();
        state.alertsEnabled = prefs.alertsEnabled();
        state.favs.clear();
        state.favs.addAll(prefs.favs());
        state.favOnly = prefs.favOnly();
        state.startFavOnly = prefs.startFavOnly();
        state.startScreenFav = prefs.startScreenFav();
        state.locationMode = prefs.locationMode();
        if (prefs.hasManualLocation()) {
            state.manualLat = prefs.manualLat();
            state.manualLon = prefs.manualLon();
            state.manualStopName = prefs.manualName();
        }
        if (prefs.alertArmed()) {
            state.alertArmed = true;
            state.alertRouteId = prefs.alertRouteId();
            state.alertDir = prefs.alertDir();
            state.alertStopIndex = prefs.alertStopIndex();
            state.alertStopName = prefs.alertStopName();
        } else {
            state.disarmAlert();
        }
        syncAlertState();
    }

    /**
     * The service may fire while this screen is open (notification sent, prefs
     * disarmed). Mirror that into the visible state so "Жду" disappears.
     */
    private void syncAlertState() {
        if (state.alertArmed && !prefs.alertArmed()) {
            state.disarmAlert();
            if (view != null) view.invalidate();
        }
    }

    /** Warns when the system froze the alert so nothing was checked. */
    private void warnIfAlertWasFrozen() {
        if (!state.alertArmed) return;
        long last = prefs.lastCheck();
        if (last <= 0) return;
        long gap = System.currentTimeMillis() - last;
        if (gap > 3 * 60 * 1000L && gap < 24 * 60 * 60 * 1000L) {
            Toast.makeText(this,
                    "Часы приостанавливали приложение: добавьте «Автобусы» в защищённые",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        applyPrefsToState();
        requestNeededPermissions();
        applyLocationPolicy();
        handler.postDelayed(fallbackTick, 5500);
        syncAlertState();
        warnIfAlertWasFrozen();
        schedule();
    }

    @Override
    protected void onUserLeaveHint() {
        // The system edge gesture / side button may leave the app from a
        // sub-screen; come back to the main list instead of a half-open route.
        if (state.screen != WatchState.SCREEN_ROUTES) {
            state.screen = WatchState.SCREEN_ROUTES;
            state.routeStops = null;
            state.board = null;
            state.favDirty = false;
            view.invalidate();
        }
        super.onUserLeaveHint();
    }

    @Override
    protected void onPause() {
        resumed = false;
        location.stop();
        handler.removeCallbacks(refreshTick);
        handler.removeCallbacks(fallbackTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (repo != null) repo.close();
        if (worker != null) worker.shutdownNow();
        super.onDestroy();
    }

    private void requestNeededPermissions() {
        List<String> want = new ArrayList<>();
        if (!hasLocationPermission()) {
            want.add(Manifest.permission.ACCESS_FINE_LOCATION);
            want.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            want.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!want.isEmpty()) {
            requestPermissions(want.toArray(new String[0]), REQ_LOCATION);
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void applyLocationPolicy() {
        if (state.autoTrack && !state.manualActive() && hasLocationPermission()) {
            location.start();
        } else {
            location.stop();
        }
    }

    private void schedule() {
        handler.removeCallbacks(refreshTick);
        if (!resumed) return;
        long delay = state.screen == WatchState.SCREEN_STOPS
                ? REFRESH_ACTIVE_MS : REFRESH_IDLE_MS;
        handler.postDelayed(refreshTick, delay);
    }

    // -------------------------------------------------------------- actions

    @Override
    public void onRouteChosen(RouteInfo route) {
        if (route == null) return;
        // remember where the board was opened from, so "back" returns there
        if (state.screen == WatchState.SCREEN_FAV) {
            state.stopsParent = WatchState.SCREEN_FAV;
        } else if (state.screen != WatchState.SCREEN_STOPS) {
            state.stopsParent = WatchState.SCREEN_ROUTES;
        }
        state.selectRoute(route);
        state.openRoute(route, 0);
        state.status = WatchState.STATUS_READY;
        view.invalidate();
        loadBoard(route, 0);
    }

    @Override
    public void onToggleDirection() {
        if (!state.canToggleDirection()) return;
        int next = state.dir == 0 ? 1 : 0;
        state.openRoute(state.selectedRoute, next);
        view.invalidate();
        loadBoard(state.selectedRoute, next);
    }

    @Override
    public void onOpenSettings() {
        state.openSettings();
        view.invalidate();
    }

    @Override
    public void onOpenStopMenu() {
        if (state.selectedStopRow() == null) return;
        state.openStopMenu();
        view.invalidate();
    }

    @Override
    public void onMenuActivate(int index) {
        if (state.screen == WatchState.SCREEN_STOP_MENU) {
            onStopMenuActivate(index);
        } else if (state.screen == WatchState.SCREEN_SETTINGS) {
            onSettingsActivate(index);
        }
    }

    @Override
    public void onStartChosen(int index) {
        boolean fav = index == 1;
        state.startFavOnly = fav;
        state.startScreenFav = fav;
        prefs.setStartScreenFav(fav);
        prefs.setStartFavOnly(fav);
        state.screen = WatchState.SCREEN_SETTINGS;
        view.invalidate();
    }

    @Override
    public void onOpenFavourites() {
        state.openFavourites();
        view.invalidate();
    }

    @Override
    public void onFavActivate(int index) {
        if (state.isAddEntry(index)) {
            state.openFavPicker();
            view.invalidate();
            return;
        }
        // a favourite route opens its stop board, like in the main list
        RouteInfo r = state.routeById(state.favSel.keyAt(index));
        if (r == null) {
            view.invalidate();
            return;
        }
        onRouteChosen(r);
    }

    @Override
    public void onFavPickActivate(int index) {
        // the row index refers to the sorted list, so resolve it through the key
        RouteInfo r = state.routeById(state.favPickSel.keyAt(index));
        if (r == null) return;
        // tapping toggles the mark: adding or removing the route
        if (state.favs.contains(r.id)) state.favs.remove(r.id);
        else state.favs.add(r.id);
        saveFavourites();
        state.updateFavDirty();
        state.refreshFavList();
        state.refreshFavPicker();
        view.invalidate();
    }

    @Override
    public void onFavAdd() {
        state.openFavPicker();
        view.invalidate();
    }

    @Override
    public void onFavSave() {
        saveFavourites();
        state.favDirty = false;
        if (state.screen == WatchState.SCREEN_FAV_PICK) {
            // finishing the add-mode returns to the favourites list
            state.screen = WatchState.SCREEN_FAV;
            state.refreshFavList();
        } else {
            state.screen = WatchState.SCREEN_ROUTES;
            applyRouteFilter();
        }
        view.invalidate();
    }

    private void saveFavourites() {
        prefs.setFavs(state.favs);
    }

    /** Applies the "favourites only" filter to the route list. */
    private void applyRouteFilter() {
        if (!state.favOnly) {
            state.setRoutes(catalogRoutes);
            return;
        }
        List<RouteInfo> only = new ArrayList<>();
        for (RouteInfo r : catalogRoutes) {
            if (state.favs.contains(r.id)) only.add(r);
        }
        state.setRoutes(only);
    }

    @Override
    public void onCityChosen(int index) {
        if (index == 0) {
            cityAuto = true;
            prefs.setCityId(0);
            state.cityAuto = true;
            if (gotFix) maybeAutoSelectCity(state.lat, state.lon);
            state.screen = WatchState.SCREEN_ROUTES;
            view.invalidate();
            return;
        }
        City chosen = index - 1 < state.cities.size() ? state.cities.get(index - 1) : null;
        if (chosen == null) return;
        cityAuto = false;
        prefs.setCity(chosen.id, chosen.name);
        state.cityAuto = false;
        switchCity(chosen, false);
        state.screen = WatchState.SCREEN_ROUTES;
        view.invalidate();
    }

    private void onStopMenuActivate(int index) {
        Board.Row row = state.selectedStopRow();
        if (row == null) return;
        switch (index) {
            case 0: {
                boolean already = state.manualActive()
                        && Geo.distanceMeters(state.manualLat, state.manualLon,
                        row.stop.lat, row.stop.lon) < 60.0;
                if (already) {
                    state.clearManualLocation();
                    prefs.clearManualLocation();
                } else {
                    state.setManualLocation(row.stop.name, row.stop.lat, row.stop.lon);
                    prefs.setManualLocation(row.stop.name, row.stop.lat, row.stop.lon);
                }
                applyLocationPolicy();
                rebuildBoardForCurrentRoute();
                view.invalidate();
                break;
            }
            case 1: {
                if (!state.alertsEnabled) break;
                boolean already = state.isAlertArmedAt(row.index);
                if (already) {
                    disarmAlert();
                } else {
                    armAlert(row.index, row.stop.name);
                }
                view.invalidate();
                break;
            }
            case 2: { // add or remove the route from the favourites
                if (state.selectedRoute == null) break;
                int id = state.selectedRoute.id;
                if (state.favs.contains(id)) {
                    state.favs.remove(id);
                } else {
                    state.favs.add(id);
                }
                saveFavourites();
                state.updateFavDirty();
                state.refreshFavList();
                view.invalidate();
                break;
            }
            case 3: // back
            default:
                state.closeMenu();
                view.invalidate();
                break;
        }
    }

    private void onSettingsActivate(int index) {
        switch (index) {
            case 0: // city picker
                state.openCities();
                view.invalidate();
                break;
            case 1: // auto-tracking
                state.autoTrack = !state.autoTrack;
                prefs.setAutoTrack(state.autoTrack);
                if (state.autoTrack && state.manualActive()) {
                    state.clearManualLocation();
                    prefs.clearManualLocation();
                    rebuildBoardForCurrentRoute();
                }
                applyLocationPolicy();
                view.invalidate();
                break;
            case 2: // arrival alerts
                state.alertsEnabled = !state.alertsEnabled;
                prefs.setAlertsEnabled(state.alertsEnabled);
                if (!state.alertsEnabled) disarmAlert();
                view.invalidate();
                break;
            case 3: // starting screen
                state.openStartScreen();
                view.invalidate();
                break;
            case 4: // back
            default:
                state.closeMenu();
                view.invalidate();
                break;
        }
    }

    private void armAlert(int stopIndex, String stopName) {
        state.armAlert(stopIndex, stopName);
        prefs.armAlert(city == null ? DEFAULT_CITY : city.id, state.selectedRoute.id,
                state.selectedRoute.name, state.dir, stopIndex, stopName);
        BusAlertService.start(this);
        BusAlertService.scheduleWatchdog(this);
        // without the notification permission the arrival alert is invisible
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_LOCATION);
        }
    }

    private void disarmAlert() {
        state.disarmAlert();
        prefs.disarmAlert();
        BusAlertService.stop(this);
    }

    @Override
    public void onBack() {
        if (state.screen == WatchState.SCREEN_STOPS) {
            // back to favourites when the board was opened from there
            state.closeStops();
            if (state.status == WatchState.STATUS_ERROR) {
                state.status = state.routes.isEmpty()
                        ? WatchState.STATUS_EMPTY : WatchState.STATUS_READY;
            }
            view.invalidate();
        } else if (state.screen == WatchState.SCREEN_CITIES) {
            state.closeMenu();
            view.invalidate();
        } else if (state.screen == WatchState.SCREEN_FAV_PICK) {
            // leaving the add-mode commits the marks, so "назад" stays "назад"
            saveFavourites();
            state.favDirty = false;
            state.screen = WatchState.SCREEN_FAV;
            state.refreshFavList();
            view.invalidate();
        } else if (state.screen == WatchState.SCREEN_FAV) {
            // leaving the favourites mode also saves the list
            saveFavourites();
            state.favDirty = false;
            state.screen = WatchState.SCREEN_ROUTES;
            applyRouteFilter();
            view.invalidate();
        } else if (state.screen == WatchState.SCREEN_STOP_MENU
                || state.screen == WatchState.SCREEN_SETTINGS) {
            int backTo = state.menuParent;
            state.closeMenu();
            if (backTo == WatchState.SCREEN_FAV) state.screen = WatchState.SCREEN_FAV;
            view.invalidate();
        } else if (state.status == WatchState.STATUS_ERROR
                || state.status == WatchState.STATUS_NO_PERMISSION) {
            state.status = state.routes.isEmpty()
                    ? WatchState.STATUS_EMPTY : WatchState.STATUS_READY;
            state.message = "";
            view.invalidate();
        } else {
            onOpenSettings();
        }
    }

    @Override
    public void onRetry() {
        requestNeededPermissions();
        if (state.screen == WatchState.SCREEN_STOPS && state.selectedRoute != null) {
            loadBoard(state.selectedRoute, state.dir);
        } else if (city == null) {
            loadInitial();
        }
    }

    private void loadBoard(final RouteInfo route, final int dir) {
        if (loading) return;
        if (!Net.isOnline(this)) {
            state.status = WatchState.STATUS_ERROR;
            state.message = Net.offlineHint();
            state.refreshing = false;
            view.invalidate();
            return;
        }
        loading = true;
        state.refreshing = true;
        view.invalidate();
        worker.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final RouteStops rs = repo.routeStops(city, route);
                    final List<Vehicle> vehicles = repo.fetchRoute(route);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            applyBoard(route, dir, rs, vehicles);
                        }
                    });
                } catch (final Exception ex) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            applyBoardError(ex);
                        }
                    });
                }
            }
        });
    }

    private void applyBoard(RouteInfo route, int dir, RouteStops rs, List<Vehicle> vehicles) {
        loading = false;
        state.refreshing = false;
        state.lastUpdate = System.currentTimeMillis();
        if (state.selectedRoute == null || state.selectedRoute.id != route.id) {
            return;
        }
        List<StopInfo> stops = rs.direction(dir);
        if (stops.isEmpty() && dir == 0) {
            stops = rs.direction(1);
            state.dir = 1;
        } else if (stops.isEmpty() && dir == 1) {
            stops = rs.direction(0);
            state.dir = 0;
        }
        List<Vehicle> filtered = new ArrayList<>();
        int busesHere = 0;
        long now = System.currentTimeMillis() / 1000L;
        long offset = FeedClock.estimateOffset(vehicles, now);
        for (Vehicle v : vehicles) {
            if (v.sleeping) continue;
            if (!FeedClock.isFresh(v, now, offset, FeedClock.MAX_AGE_SEC)) continue;
            filtered.add(v);
            if (v.dir == state.dir) busesHere++;
        }
        Board board = Board.build(stops, filtered, state.dir, state.effLat(), state.effLon(),
                now, FeedClock.MAX_AGE_SEC);
        state.setBoard(rs, board, board.userStopIndex, busesHere);
        state.status = WatchState.STATUS_READY;
        view.invalidate();
        schedule();
    }

    private void applyBoardError(Exception ex) {
        loading = false;
        state.refreshing = false;
        if (state.selectedRoute != null && state.board == null) {
            state.status = WatchState.STATUS_ERROR;
            state.message = "Нет связи с сервером";
        }
        view.invalidate();
        schedule();
    }

    private void rebuildBoardForCurrentRoute() {
        if (state.selectedRoute == null || state.routeStops == null || state.board == null) return;
        List<StopInfo> stops = state.routeStops.direction(state.dir);
        Board board = Board.build(stops, currentVehicles(), state.dir,
                state.effLat(), state.effLon(), System.currentTimeMillis() / 1000L,
                FeedClock.MAX_AGE_SEC);
        state.setBoard(state.routeStops, board, board.userStopIndex, state.busesHereCount);
        view.invalidate();
    }

    private List<Vehicle> currentVehicles() {
        List<Vehicle> out = new ArrayList<>();
        if (state.board == null) return out;
        for (Board.Row row : state.board.rows) {
            out.addAll(row.atStop);
            if (row.nextBus != null) out.add(row.nextBus);
        }
        // every bus is referenced by many rows; keep one copy per vehicle
        return Board.unique(out);
    }

    private void refreshBoard(boolean manual) {
        syncAlertState();
        if (loading || !resumed) return;
        if (state.screen != WatchState.SCREEN_STOPS || state.selectedRoute == null) return;
        loadBoard(state.selectedRoute, state.dir);
    }

    // ---------------------------------------------------------- event relay

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (view != null && view.dispatchGenericMotionEvent(event)) return true;
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // every press of a hardware side button gives a short vibration,
        // whatever the button ends up doing
        if (view != null && isSideButton(keyCode) && event.getRepeatCount() == 0) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
        if (view != null && view.dispatchKeyEvent(event)) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_STEM_1:
            case KeyEvent.KEYCODE_STEM_2:
            case KeyEvent.KEYCODE_STEM_3:
            case KeyEvent.KEYCODE_MENU:
                if (view != null) {
                    // lower side button: closes the settings back to the screen
                    // they were opened from; opens them from the favourites
                    if (state.screen == WatchState.SCREEN_SETTINGS) {
                        onBack();
                        return true;
                    }
                    if (state.inFavourites()) {
                        onOpenSettings();
                        return true;
                    }
                    view.showContextMenuFromKey();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Watch side buttons: stems, menu and the primary button. */
    private static boolean isSideButton(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_STEM_1
                || keyCode == KeyEvent.KEYCODE_STEM_2
                || keyCode == KeyEvent.KEYCODE_STEM_3
                || keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_STEM_PRIMARY;
    }

    @Override
    public void onBackPressed() {
        if (view != null && view.recentlyHandledBack()) return;
        // Huawei's lower side button sends BACK: on the favourites screen it
        // opens the settings, like the side button on the main screen
        if (state.inFavourites()) {
            onOpenSettings();
            return;
        }
        if (state.screen != WatchState.SCREEN_ROUTES) {
            onBack();
            return;
        }
        onOpenSettings();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        if (hasLocationPermission()) {
            applyLocationPolicy();
        } else {
            state.status = WatchState.STATUS_NO_PERMISSION;
            state.message = "Нет доступа к геопозиции";
            state.refreshing = false;
            view.invalidate();
        }
    }
}
