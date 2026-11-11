package ru.krsk.buswatch;

/** A city supported by the app (id = Bustime place id). */
public final class City {
    public final int id;
    public final String slug;
    public final String name;
    public final double lat;
    public final double lon;
    public final int routeCount;
    public final int stopCount;

    public City(int id, String slug, String name, double lat, double lon,
                int routeCount, int stopCount) {
        this.id = id;
        this.slug = slug == null ? "" : slug;
        this.name = name == null ? "" : name;
        this.lat = lat;
        this.lon = lon;
        this.routeCount = routeCount;
        this.stopCount = stopCount;
    }
}
