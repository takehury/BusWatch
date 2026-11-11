package ru.krsk.buswatch;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists user settings and the armed bus-arrival alert across restarts. */
public final class Prefs {

    private static final String NAME = "krsk_buswatch";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** 0 = city chosen automatically by GPS. */
    public int cityId() {
        return sp.getInt("city_id", 0);
    }

    public void setCityId(int id) {
        sp.edit().putInt("city_id", id).apply();
    }

    public String cityName() {
        return sp.getString("city_name", "");
    }

    public void setCity(int id, String name) {
        sp.edit().putInt("city_id", id).putString("city_name", name).apply();
    }

    /** Epoch millis of the last successful alert check (diagnostics). */
    public long lastCheck() {
        return sp.getLong("alert_last_check", 0L);
    }

    public void setLastCheck(long millis) {
        sp.edit().putLong("alert_last_check", millis).apply();
    }

    public boolean autoTrack() {
        return sp.getBoolean("auto_track", true);
    }

    public void setAutoTrack(boolean v) {
        sp.edit().putBoolean("auto_track", v).apply();
    }

    public boolean alertsEnabled() {
        return sp.getBoolean("alerts_enabled", true);
    }

    public void setAlertsEnabled(boolean v) {
        sp.edit().putBoolean("alerts_enabled", v).apply();
    }

    public int locationMode() {
        return sp.getInt("loc_mode", WatchState.LOC_AUTO);
    }

    public void setLocationMode(int v) {
        sp.edit().putInt("loc_mode", v).apply();
    }

    public boolean hasManualLocation() {
        return sp.contains("manual_lat") && sp.contains("manual_lon");
    }

    public double manualLat() {
        return Double.longBitsToDouble(sp.getLong("manual_lat", 0));
    }

    public double manualLon() {
        return Double.longBitsToDouble(sp.getLong("manual_lon", 0));
    }

    public String manualName() {
        return sp.getString("manual_name", "");
    }

    public void setManualLocation(String name, double lat, double lon) {
        sp.edit()
                .putString("manual_name", name)
                .putLong("manual_lat", Double.doubleToLongBits(lat))
                .putLong("manual_lon", Double.doubleToLongBits(lon))
                .putInt("loc_mode", WatchState.LOC_MANUAL)
                .apply();
    }

    public void clearManualLocation() {
        sp.edit().remove("manual_name").remove("manual_lat").remove("manual_lon")
                .putInt("loc_mode", WatchState.LOC_AUTO).apply();
    }

    // ---- armed alert -----------------------------------------------------

    public boolean alertArmed() {
        return sp.getBoolean("alert_armed", false);
    }

    public int alertCityId() {
        return sp.getInt("alert_city", 3);
    }

    public int alertRouteId() {
        return sp.getInt("alert_route", 0);
    }

    public int alertDir() {
        return sp.getInt("alert_dir", 0);
    }

    public int alertStopIndex() {
        return sp.getInt("alert_stop_index", -1);
    }

    public String alertStopName() {
        return sp.getString("alert_stop_name", "");
    }

    /** Public route number ("2"), as opposed to the feed's internal bus id. */
    public String alertRouteNumber() {
        return sp.getString("alert_route_number", "");
    }

    public void armAlert(int cityId, int routeId, String routeNumber, int dir,
                         int stopIndex, String stopName) {
        sp.edit()
                .putBoolean("alert_armed", true)
                .putInt("alert_city", cityId)
                .putInt("alert_route", routeId)
                .putString("alert_route_number", routeNumber == null ? "" : routeNumber)
                .putInt("alert_dir", dir)
                .putInt("alert_stop_index", stopIndex)
                .putString("alert_stop_name", stopName)
                .apply();
    }

    public void disarmAlert() {
        sp.edit().putBoolean("alert_armed", false).apply();
    }
}
