package ru.krsk.buswatch;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Connectivity probe. Watches reach the server over Bluetooth tethering,
 * Wi-Fi or LTE/eSIM — all of them surface as the active default network, so a
 * single capability check covers every case.
 */
public final class Net {

    private Net() {
    }

    public static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true; // cannot tell: let the request try
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception ex) {
            return true;
        }
    }

    /** Human hint about the transports a watch usually has. */
    public static String offlineHint() {
        return "Нет сети: проверьте Wi-Fi, Bluetooth или LTE";
    }
}
