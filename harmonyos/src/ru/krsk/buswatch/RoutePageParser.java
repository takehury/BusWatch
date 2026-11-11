package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a Bustime route page into the ordered stop list of both directions.
 *
 * Only cities with a pre-bundled route_stops.json avoid this: for every other
 * city the ordered stops are read from the route page's inline JS
 * (`var route0`, `var route1`, `var busstops`) and cached by the repository.
 */
public final class RoutePageParser {

    private static final Pattern ROUTE0 = Pattern.compile(
            "var\\s+route0\\s*=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);
    private static final Pattern ROUTE1 = Pattern.compile(
            "var\\s+route1\\s*=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);
    private static final Pattern STOP_ENTRY = Pattern.compile(
            "(\\d+)\\s*:\\s*\\{[^{}]*?name\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
                    + "[^{}]*?x\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*y\\s*:\\s*(-?\\d+(?:\\.\\d+)?)",
            Pattern.DOTALL);

    private RoutePageParser() {
    }

    public static RouteStops parse(int routeId, String html) {
        List<StopInfo> dir0 = build(route0(html), stops(html));
        List<StopInfo> dir1 = build(route1(html), stops(html));
        return new RouteStops(routeId, dir0, dir1);
    }

    static List<Integer> route0(String html) {
        return ids(ROUTE0, html);
    }

    static List<Integer> route1(String html) {
        return ids(ROUTE1, html);
    }

    private static List<Integer> ids(Pattern p, String html) {
        List<Integer> out = new ArrayList<>();
        if (html == null) return out;
        Matcher m = p.matcher(html);
        if (!m.find()) return out;
        Matcher n = Pattern.compile("\\d+").matcher(m.group(1));
        while (n.find()) {
            try {
                out.add(Integer.parseInt(n.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static Map<Integer, StopInfo> stops(String html) {
        Map<Integer, StopInfo> map = new HashMap<>();
        if (html == null) return map;
        int start = html.indexOf("var busstops");
        if (start < 0) return map;
        int brace = html.indexOf('{', start);
        if (brace < 0) return map;
        int end = matchingBrace(html, brace);
        String body = end > brace ? html.substring(brace + 1, end) : html.substring(brace + 1);
        Matcher m = STOP_ENTRY.matcher(body);
        while (m.find()) {
            try {
                int id = Integer.parseInt(m.group(1));
                String name = unescape(m.group(2));
                double lon = Double.parseDouble(m.group(3));
                double lat = Double.parseDouble(m.group(4));
                map.put(id, new StopInfo(id, name, "", lat, lon, null));
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    private static int matchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return s.length();
    }

    private static List<StopInfo> build(List<Integer> ids, Map<Integer, StopInfo> stops) {
        List<StopInfo> out = new ArrayList<>();
        for (Integer id : ids) {
            StopInfo s = stops.get(id);
            if (s != null) out.add(s);
        }
        return out;
    }

    static String unescape(String s) {
        if (s == null || s.indexOf('\\') < 0) return s == null ? "" : s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char e = s.charAt(++i);
            switch (e) {
                case 'n':
                    sb.append(' ');
                    break;
                case 't':
                    sb.append(' ');
                    break;
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ex) {
                            sb.append('u');
                        }
                    }
                    break;
                default:
                    sb.append(e);
            }
        }
        return sb.toString();
    }
}
