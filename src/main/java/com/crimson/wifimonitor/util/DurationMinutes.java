package com.crimson.wifimonitor.util;

public final class DurationMinutes {

    private DurationMinutes() {}

    public static Double fromMillis(Long millis) {
        if (millis == null) {
            return null;
        }
        return fromMillis(millis.doubleValue());
    }

    public static Double fromMillis(double millis) {
        return round2(millis / 60_000.0);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
