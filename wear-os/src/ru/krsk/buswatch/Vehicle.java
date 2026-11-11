package ru.krsk.buswatch;

/** One live vehicle of a route. Positions come from the operator's GLONASS feed. */
public final class Vehicle {
    public final String uid;
    public final int routeId;
    public final int routeType;
    public final String routeName;
    public final String routeTitle;
    public final double lat;
    public final double lon;
    public final int speedKmh;
    public final int heading;
    public final String nextStop;
    public final String plate;
    public final int dir;
    public final long timestampSec;
    public final boolean sleeping;
    public final long stopId;   // feed "b": id of the stop the vehicle is at/approaching
    public final int order;     // feed "order": index of that stop in the direction list

    /** Filled by the repository when the list is built. */
    public double distanceMeters;

    public Vehicle(String uid, int routeId, String routeName, String routeTitle,
                   double lat, double lon, int speedKmh, int heading,
                   String nextStop, String plate, int dir, long timestampSec,
                   boolean sleeping) {
        this(uid, routeId, 0, routeName, routeTitle, lat, lon, speedKmh, heading,
                nextStop, plate, dir, timestampSec, sleeping, 0, -1);
    }

    public Vehicle(String uid, int routeId, int routeType, String routeName, String routeTitle,
                   double lat, double lon, int speedKmh, int heading,
                   String nextStop, String plate, int dir, long timestampSec,
                   boolean sleeping, long stopId, int order) {
        this.uid = uid == null ? "" : uid;
        this.routeId = routeId;
        this.routeType = routeType;
        this.routeName = routeName == null ? "?" : routeName;
        this.routeTitle = routeTitle == null ? "" : routeTitle;
        this.lat = lat;
        this.lon = lon;
        this.speedKmh = speedKmh;
        this.heading = heading;
        this.nextStop = nextStop == null ? "" : nextStop;
        this.plate = plate == null ? "" : plate;
        this.dir = dir;
        this.timestampSec = timestampSec;
        this.sleeping = sleeping;
        this.stopId = stopId;
        this.order = order;
    }

    public String destination() {
        String[] parts = routeTitle.trim().split("\\s*(?:↔|⇄|—|–|-)+\\s*");
        if (parts.length >= 2) {
            String a = parts[0].trim();
            String b = parts[parts.length - 1].trim();
            return dir == 0 ? b : a;
        }
        return routeTitle;
    }

    public int etaMinutes() {
        return Geo.etaMinutes(distanceMeters);
    }

    public String key() {
        return routeId + ":" + (uid.isEmpty() ? (lat + "," + lon) : uid);
    }
}
