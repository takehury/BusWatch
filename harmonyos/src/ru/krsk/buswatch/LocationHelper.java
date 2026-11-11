package ru.krsk.buswatch;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/**
 * Small LocationManager wrapper (no Google Play Services on the watch).
 * Delivers the best last-known fix immediately and then live updates.
 */
public final class LocationHelper implements LocationListener {

    public interface Listener {
        void onFix(double lat, double lon, int accuracyM, boolean fromGps);
    }

    private final Context context;
    private final LocationManager manager;
    private final Listener listener;
    private boolean running;

    public LocationHelper(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        this.listener = listener;
    }

    public boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void start() {
        if (running || !hasPermission() || manager == null) return;
        running = true;
        Location best = null;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER}) {
                if (!manager.isProviderEnabled(p)) continue;
                Location l = manager.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {
        }
        if (best != null) {
            emit(best, LocationManager.GPS_PROVIDER.equals(best.getProvider()));
        }
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 2f, this);
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10f, this);
            }
        } catch (SecurityException ignored) {
        }
    }

    public void stop() {
        if (!running || manager == null) return;
        running = false;
        try {
            manager.removeUpdates(this);
        } catch (SecurityException ignored) {
        }
    }

    private void emit(Location l, boolean fromGps) {
        if (l == null) return;
        listener.onFix(l.getLatitude(), l.getLongitude(),
                l.hasAccuracy() ? Math.round(l.getAccuracy()) : -1, fromGps);
    }

    @Override
    public void onLocationChanged(Location location) {
        emit(location, location != null
                && LocationManager.GPS_PROVIDER.equals(location.getProvider()));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
