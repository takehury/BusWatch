package ru.krsk.buswatch;

/** A stop: either from the city registry or from a route's ordered stop list. */
public final class StopInfo {
    public final long id;      // stop id used by the live feed (0 when unknown)
    public final String name;
    public final String slug;
    public final double lat;
    public final double lon;
    public final int[] routeIdx;

    public StopInfo(long id, String name, String slug, double lat, double lon, int[] routeIdx) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.slug = slug == null ? "" : slug;
        this.lat = lat;
        this.lon = lon;
        this.routeIdx = routeIdx == null ? new int[0] : routeIdx;
    }

    public StopInfo(String name, String slug, double lat, double lon, int[] routeIdx) {
        this(0, name, slug, lat, lon, routeIdx);
    }
}
