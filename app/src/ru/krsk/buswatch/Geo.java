package ru.krsk.buswatch;

/** Geodesy and human-friendly formatting helpers (pure Java, unit-testable). */
public final class Geo {

    public static final double EARTH_R = 6371000.0;

    /** Krasnoyarsk city centre (pl. Revolyutsii). */
    public static final double CITY_LAT = 56.0106;
    public static final double CITY_LON = 92.8526;

    private Geo() {
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_R * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Initial bearing in degrees, 0 = north. */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360.0) % 360.0;
    }

    /** True when the point is inside the Krasnoyarsk city bounding box. */
    public static boolean inCity(double lat, double lon) {
        return lat >= 55.90 && lat <= 56.16 && lon >= 92.55 && lon <= 93.12;
    }

    /** "850 м", "1,2 км", "12 км". */
    public static String formatDistance(double meters) {
        if (Double.isNaN(meters) || meters < 0) return "—";
        if (meters < 950) {
            int m = (int) (Math.round(meters / 10.0) * 10);
            if (m == 0) m = 5;
            return m + " м";
        }
        double km = meters / 1000.0;
        if (km < 9.95) {
            return String.format(java.util.Locale.US, "%.1f", km).replace('.', ',') + " км";
        }
        return Math.round(km) + " км";
    }

    /** Rough arrival estimate for a straight-line distance, walking-speed independent. */
    public static int etaMinutes(double meters) {
        double km = Math.max(0, meters) / 1000.0;
        // 20 km/h average including stops, plus half a minute of rounding.
        int min = (int) Math.round(km / 20.0 * 60.0 + 0.5);
        return clamp(min, 1, 90);
    }

    public static String formatEta(int minutes) {
        if (minutes < 1) return "меньше минуты";
        return "~" + minutes + " " + Text.plural(minutes, "минута", "минуты", "минут");
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Compass point for a heading: С, СВ, В, ... */
    public static String compass(int heading) {
        String[] dirs = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        int idx = (int) Math.round(((heading % 360) + 360) % 360 / 45.0) % 8;
        return dirs[idx];
    }
}
