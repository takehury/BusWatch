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

    /**
     * Decodes the HTML entities Bustime uses in route titles
     * (&harr; → ↔, &amp; → &, &quot; → " and numeric ones).
     */
    public static String html(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '&') {
                sb.append(c);
                continue;
            }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 10) {
                sb.append(c);
                continue;
            }
            String name = s.substring(i + 1, semi);
            String rep = entity(name);
            if (rep == null) {
                sb.append(c);
            } else {
                sb.append(rep);
                i = semi;
            }
        }
        return sb.toString();
    }

    private static String entity(String name) {
        if (name.isEmpty()) return null;
        if ("harr".equals(name) || "hArr".equals(name)) return "\u2194"; // ↔
        if ("larr".equals(name)) return "\u2190";                       // ←
        if ("rarr".equals(name)) return "\u2192";                       // →
        if ("amp".equals(name)) return "&";
        if ("quot".equals(name)) return "\"";
        if ("apos".equals(name)) return "'";
        if ("laquo".equals(name)) return "\u00ab";
        if ("raquo".equals(name)) return "\u00bb";
        if ("nbsp".equals(name)) return " ";
        if ("middot".equals(name)) return "\u00b7";
        if ("ndash".equals(name)) return "\u2013";
        if ("mdash".equals(name)) return "\u2014";
        if (name.startsWith("#")) {
            try {
                int code = name.startsWith("#x") || name.startsWith("#X")
                        ? Integer.parseInt(name.substring(2), 16)
                        : Integer.parseInt(name.substring(1));
                return new String(Character.toChars(code));
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    public static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
