package com.crimson.wifimonitor.windows;

public record WlanInterfaceInfo(
        String name,
        String description,
        String state,
        String ssid,
        String bssid,
        String radioType,
        String authentication,
        String channel,
        String receiveRateMbps,
        String transmitRateMbps,
        Integer signalPercent,
        String profile,
        String rawOutput) {

    public boolean connected() {
        return state != null && state.equalsIgnoreCase("connected");
    }
}
