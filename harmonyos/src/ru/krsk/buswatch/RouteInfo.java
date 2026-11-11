package ru.krsk.buswatch;

/** A Krasnoyarsk route (bus / trolleybus / tram). */
public final class RouteInfo {
    public final int id;        // internal Bustime bus_id used by the live feed
    public final String name;   // "2", "18С", "Тм 4"
    public final int type;      // 0 bus, 1 trolleybus, 2 tram, 3 minibus
    public final String slug;
    public final String title;  // "Автовокзал Восточный ↔ Дом учёных"

    public RouteInfo(int id, String name, int type, String slug, String title) {
        this.id = id;
        this.name = name == null ? "?" : name;
        this.type = type;
        this.slug = slug == null ? "" : slug;
        this.title = title == null ? "" : title;
    }

    public String typeLabel() {
        switch (type) {
            case 1:
                return "Троллейбус";
            case 2:
                return "Трамвай";
            case 3:
                return "Маршрутка";
            default:
                return "Автобус";
        }
    }

    /** Destination for a given direction bit (0/1) taken from the route title. */
    public String destination(int dir) {
        String t = title.trim();
        if (t.isEmpty()) return "";
        String[] parts = t.split("\\s*(?:↔|⇄|—|–|-)+\\s*");
        if (parts.length >= 2) {
            String a = parts[0].trim();
            String b = parts[parts.length - 1].trim();
            return dir == 0 ? b : a;
        }
        return t;
    }

    /** True for routes the feed can actually provide vehicles for. */
    public boolean isGround() {
        return type == 0 || type == 1 || type == 2 || type == 3;
    }
}
