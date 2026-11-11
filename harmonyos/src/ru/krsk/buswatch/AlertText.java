package ru.krsk.buswatch;

/**
 * Formats notification texts. Kept as pure Java so the route-number mapping
 * (the feed's internal bus_id is NOT the route number) can be unit-tested.
 */
public final class AlertText {

    private AlertText() {
    }

    /**
     * Human route number ("2", "18С") for notifications. The stored value is
     * preferred; the internal bus id is only a last-resort fallback.
     */
    public static String routeNumber(String storedNumber, int routeId) {
        String n = storedNumber == null ? "" : storedNumber.trim();
        if (!n.isEmpty()) return n;
        return String.valueOf(routeId);
    }

    public static String foregroundTitle(String number) {
        return "Ожидание автобуса №" + number;
    }

    public static String arrivalTitle() {
        return "Автобус прибывает";
    }

    public static String arrivalText(String number, String stopName) {
        String stop = stopName == null ? "" : stopName;
        return "Маршрут №" + number + " на остановке «" + stop + "»";
    }
}
