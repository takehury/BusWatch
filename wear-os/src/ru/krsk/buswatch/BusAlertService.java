package ru.krsk.buswatch;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground service that watches for a bus to arrive at the marked stop and
 * keeps working with the screen off.
 *
 * Android suspends network access in Doze and may kill background work, so the
 * service combines three mechanisms:
 *  - a foreground notification (keeps the process alive),
 *  - a partial wake lock (keeps the CPU running between ticks),
 *  - an exact, "allow while idle" alarm watchdog (wakes the device in Doze and
 *    restarts polling even if the process was killed).
 */
public final class BusAlertService extends Service {

    public static final String ACTION_START = "ru.krsk.buswatch.ALERT_START";
    public static final String ACTION_TICK = "ru.krsk.buswatch.ALERT_TICK";

    private static final int NOTIF_ID = 42;
    private static final int NOTIF_ARRIVAL_ID = 43;
    private static final String CH_SERVICE = "bus_alert_service_v2";
    private static final String CH_ARRIVAL = "bus_alerts_v2";
    private static final long POLL_MS = 5000L;
    private static final long WATCHDOG_MS = 60000L;
    private static final long MAX_LIFETIME_MS = 3 * 60 * 60 * 1000L;
    private static final int ALARM_REQ = 7;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private BusRepository repo;
    private Catalog catalog;
    private City city;
    private Alert alert;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean running;
    private final AtomicBoolean tickBusy = new AtomicBoolean(false);
    private long startedAt;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            worker.submit(new Runnable() {
                @Override
                public void run() {
                    if (!tickBusy.compareAndSet(false, true)) return;
                    boolean keepGoing;
                    try {
                        keepGoing = tick();
                    } finally {
                        tickBusy.set(false);
                    }
                    if (keepGoing && running) {
                        handler.postDelayed(poll, POLL_MS);
                    }
                }
            });
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        repo = new BusRepository(this);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "krskbus:alert");
            wakeLock.setReferenceCounted(false);
        }
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Prefs prefs = new Prefs(this);
        if (!prefs.alertsEnabled() || !prefs.alertArmed()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildForegroundNotification(prefs));
        if (!running) {
            running = true;
            startedAt = SystemClock.elapsedRealtime();
            alert = new Alert(prefs.alertStopIndex());
            holdWakeLock();
        }
        // both the first start and every watchdog alarm tick poll immediately
        handler.removeCallbacks(poll);
        handler.post(poll);
        scheduleWatchdog();
        return START_STICKY;
    }

    /** @return true when polling should continue. */
    private boolean tick() {
        Prefs prefs = new Prefs(this);
        if (!prefs.alertsEnabled() || !prefs.alertArmed()) {
            shutdown();
            return false;
        }
        if (SystemClock.elapsedRealtime() - startedAt > MAX_LIFETIME_MS) {
            prefs.disarmAlert();
            shutdown();
            return false;
        }
        holdWakeLock();
        try {
            if (city == null) {
                List<City> all = repo.loadCities();
                city = Cities.byId(all, prefs.alertCityId());
                if (city == null && !all.isEmpty()) city = all.get(0);
            }
            if (city == null) return true;
            if (catalog == null) catalog = repo.loadCity(city);
            RouteInfo route = catalog.route(prefs.alertRouteId());
            if (route == null) return true;
            RouteStops rs = repo.routeStops(city, route);
            List<StopInfo> stops = rs.direction(prefs.alertDir());
            if (stops.isEmpty()) return true;
            List<Vehicle> vehicles = repo.fetchRoute(route);
            if (vehicles == null || vehicles.isEmpty()) return true;
            long now = System.currentTimeMillis() / 1000L;
            java.util.Map<String, Integer> orders =
                    Board.resolveVehicleOrders(stops, vehicles, prefs.alertDir(), now,
                            FeedClock.MAX_AGE_SEC);
            updateOngoing(prefs, orders.size(), now);
            prefs.setLastCheck(now * 1000L);
            if (alert == null || alert.stopIndex() != prefs.alertStopIndex()) {
                alert = new Alert(prefs.alertStopIndex());
            }
            if (alert.check(orders)) {
                fire(prefs);
                return false;
            }
        } catch (Exception ex) {
            // transient network error; retry on the next tick
            android.util.Log.w("BusAlert", "tick failed: " + ex);
        }
        return true;
    }

    private void fire(Prefs prefs) {
        android.util.Log.i("BusAlert", "arrival detected, firing");
        vibrateTwice();
        postArrivalNotification(prefs);
        prefs.disarmAlert();
        shutdown();
    }

    private void shutdown() {
        cancelWatchdog();
        releaseWakeLock();
        running = false;
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        stopSelf();
    }

    private void vibrateTwice() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                long[] pattern = new long[]{0, 150, 120, 150};
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1),
                            new android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build());
                } else {
                    v.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel service = new NotificationChannel(CH_SERVICE,
                "Ожидание автобуса", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Постоянное уведомление, пока идёт ожидание");
        service.setShowBadge(false);
        nm.createNotificationChannel(service);

        NotificationChannel arrival = new NotificationChannel(CH_ARRIVAL,
                "Прибытие автобуса", NotificationManager.IMPORTANCE_HIGH);
        arrival.setDescription("Вибрация и уведомление, когда автобус подошёл");
        arrival.enableVibration(true);
        arrival.setVibrationPattern(new long[]{0, 150, 120, 150});
        arrival.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(arrival);
    }

    /** Route number for notifications: stored "2", never the internal bus id. */
    private String routeNumber(Prefs prefs) {
        String stored = prefs.alertRouteNumber();
        if (stored != null && !stored.trim().isEmpty()) return stored.trim();
        if (catalog != null) {
            RouteInfo route = catalog.route(prefs.alertRouteId());
            if (route != null) return AlertText.routeNumber(route.name, prefs.alertRouteId());
        }
        return AlertText.routeNumber(prefs.alertRouteNumber(), prefs.alertRouteId());
    }

    private Notification buildForegroundNotification(Prefs prefs) {
        return buildNotification(CH_SERVICE,
                AlertText.foregroundTitle(routeNumber(prefs)),
                "остановка: " + prefs.alertStopName(), true, false);
    }

    private void postArrivalNotification(Prefs prefs) {
        String text = AlertText.arrivalText(routeNumber(prefs), prefs.alertStopName());
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ARRIVAL_ID, buildNotification(CH_ARRIVAL,
                    AlertText.arrivalTitle(), text, false, true));
        }
    }

    /** Refreshes the ongoing notification so the user can see it is alive. */
    private void updateOngoing(Prefs prefs, int buses, long nowSec) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            String time = Text.clock(nowSec * 1000L);
            String text = "остановка: " + prefs.alertStopName()
                    + " · " + buses + " маш. · " + time;
            nm.notify(NOTIF_ID, buildNotification(CH_SERVICE,
                    AlertText.foregroundTitle(routeNumber(prefs)), text, true, false));
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(String channel, String title, String text,
                                           boolean ongoing, boolean vibrate) {
        Intent open = new Intent(this, BusWatchActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channel)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_bus)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setOnlyAlertOnce(ongoing)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(vibrate ? Notification.PRIORITY_MAX : Notification.PRIORITY_LOW);
            if (vibrate) b.setVibrate(new long[]{0, 150, 120, 150});
        }
        return b.build();
    }

    private static PendingIntent watchdogIntent(Context ctx) {
        Intent i = new Intent(ctx, AlertAlarmReceiver.class).setAction(ACTION_TICK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, ALARM_REQ, i, flags);
    }

    /**
     * Schedules the watchdog without touching the service. Called by the
     * activity right after arming, so the alarm survives even if the system
     * kills the service when the screen goes off.
     */
    public static void scheduleWatchdog(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + WATCHDOG_MS;
        PendingIntent pi = watchdogIntent(ctx);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception first) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) {
            }
        }
    }

    public static void cancelWatchdog(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
        if (am == null) return;
        try {
            am.cancel(watchdogIntent(ctx));
        } catch (Exception ignored) {
        }
    }

    private void scheduleWatchdog() {
        scheduleWatchdog(this);
    }

    private void cancelWatchdog() {
        cancelWatchdog(this);
    }

    private void holdWakeLock() {
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                // no timeout: the alert lives at most MAX_LIFETIME_MS, checked in tick()
                wakeLock.acquire();
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // keep waiting even if the app was swiped away
        if (running) {
            scheduleWatchdog();
            handler.post(poll);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        Prefs prefs = new Prefs(this);
        if (!prefs.alertArmed() || !prefs.alertsEnabled()) {
            cancelWatchdog();
        }
        // still armed: leave the alarm armed so it restarts the service
        handler.removeCallbacksAndMessages(null);
        releaseWakeLock();
        worker.shutdownNow();
        if (repo != null) repo.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // -------------------------------------------------------------- helpers

    public static void start(Context context) {
        Intent i = new Intent(context, BusAlertService.class);
        i.setAction(ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        } catch (Exception first) {
            try {
                context.startService(i);
            } catch (Exception second) {
                android.util.Log.e("BusAlert", "cannot start service: " + second);
            }
        }
    }

    /** Stops the service without creating it when it is not running. */
    public static void stop(Context context) {
        cancelWatchdog(context);
        context.stopService(new Intent(context, BusAlertService.class));
    }
}
