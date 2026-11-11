package ru.krsk.buswatch;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Russian text helpers. */
public final class Text {

    private Text() {
    }

    /** Russian plural form: 1 минута, 2 минуты, 5 минут. */
    public static String plural(int n, String one, String few, String many) {
        int mod10 = Math.abs(n) % 10;
        int mod100 = Math.abs(n) % 100;
        if (mod10 == 1 && mod100 != 11) return one;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
        return many;
    }

    public static String clock(long millis) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Krasnoyarsk"), Locale.ROOT);
        c.setTimeInMillis(millis);
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    /** "сейчас", "12 с назад", "3 мин назад". */
    public static String ago(long millis) {
        long sec = Math.max(0, (System.currentTimeMillis() - millis) / 1000L);
        if (sec < 5) return "сейчас";
        if (sec < 60) return sec + " с назад";
        long min = sec / 60;
        return min + " " + plural((int) min, "минуту", "минуты", "минут") + " назад";
    }

    /** Trims a string to a character budget, adding an ellipsis. */
    public static String ellipsize(String s, int maxChars) {
        if (s == null) return "";
        if (maxChars <= 1) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, Math.max(0, maxChars - 1)).trim() + "…";
    }

    /** Ночь по Красноярску: общественный транспорт не ходит. */
    public static boolean isNight() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Krasnoyarsk"));
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= 22 || h < 6;
    }

    public static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
