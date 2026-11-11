package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, dependency-free JSON parser used by the watch app.
 *
 * Android ships org.json, but keeping the parser in plain Java lets the very
 * same code run in the host-side test harness (no emulator required).
 * Supports the subset of JSON produced by the public transport feed:
 * objects, arrays, strings (with escapes), numbers, booleans and null.
 */
public final class MiniJson {

    private MiniJson() {
    }

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("null json");
        Parser p = new Parser(text);
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) throw p.error("trailing data");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String asString(Object o, String def) {
        return o instanceof String ? (String) o : def;
    }

    public static double asDouble(Object o, double def) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return def;
    }

    public static int asInt(Object o, int def) {
        if (o instanceof Number) return (int) Math.round(((Number) o).doubleValue());
        return def;
    }

    public static long asLong(Object o, long def) {
        if (o instanceof Number) return Math.round(((Number) o).doubleValue());
        return def;
    }

    public static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).doubleValue() != 0;
        return def;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        IllegalArgumentException error(String msg) {
            int from = Math.max(0, i - 20);
            int to = Math.min(s.length(), i + 20);
            return new IllegalArgumentException(msg + " at " + i + " near: "
                    + s.substring(from, to).replace('\n', ' '));
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) throw error("unexpected end");
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return parseNumber();
            }
        }

        void expect(String word) {
            if (!s.startsWith(word, i)) throw error("expected " + word);
            i += word.length();
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (!atEnd() && s.charAt(i) == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                if (atEnd() || s.charAt(i) != '"') throw error("object key expected");
                String key = parseString();
                skipWs();
                if (atEnd() || s.charAt(i) != ':') throw error("':' expected");
                i++;
                map.put(key, parseValue());
                skipWs();
                if (atEnd()) throw error("unterminated object");
                char c = s.charAt(i++);
                if (c == '}') return map;
                if (c != ',') throw error("',' expected");
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (!atEnd() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (atEnd()) throw error("unterminated array");
                char c = s.charAt(i++);
                if (c == ']') return list;
                if (c != ',') throw error("',' expected");
            }
        }

        String parseString() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw error("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) throw error("bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (i + 4 > s.length()) throw error("bad unicode escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad unicode escape");
                        }
                        i += 4;
                        break;
                    default:
                        throw error("bad escape \\" + e);
                }
            }
        }

        Object parseNumber() {
            int start = i;
            if (!atEnd() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            while (!atEnd()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                        || c == '+' || c == '-') {
                    i++;
                } else {
                    break;
                }
            }
            if (start == i) throw error("number expected");
            String num = s.substring(start, i);
            try {
                return Double.valueOf(num);
            } catch (NumberFormatException ex) {
                throw error("bad number " + num);
            }
        }
    }
}
