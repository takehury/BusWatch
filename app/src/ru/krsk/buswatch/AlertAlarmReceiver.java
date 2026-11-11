package ru.krsk.buswatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Watchdog alarm target. Wakes the device in Doze and restarts polling.
 * A receiver is used (instead of a service PendingIntent) because a broadcast
 * from an exact alarm gets a short foreground-service allowance, which makes
 * startForegroundService reliable on Android 12+.
 */
public final class AlertAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, BusAlertService.class)
                .setAction(BusAlertService.ACTION_TICK);
        try {
            context.startForegroundService(service);
        } catch (Exception first) {
            try {
                context.startService(service);
            } catch (Exception ignored) {
            }
        }
    }
}
