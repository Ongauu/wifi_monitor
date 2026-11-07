package com.crimson.wifimonitor.windows;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WlanInterfaceParser {

    private static final Pattern FIELD = Pattern.compile("^\\s*([^:]+)\\s*:\\s*(.*)\\s*$");
    private static final Pattern SIGNAL = Pattern.compile("(\\d+)");

    public WlanInterfaceInfo parse(String output) {
        if (output == null || output.isBlank()) {
            return empty(output);
        }

        String name = null;
        String description = null;
        String state = null;
        String ssid = null;
        String bssid = null;
        String radioType = null;
        String authentication = null;
        String channel = null;
        String receiveRate = null;
        String transmitRate = null;
        Integer signal = null;
        String profile = null;

        for (String line : output.split("\\R")) {
            Matcher matcher = FIELD.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String key = normalize(matcher.group(1));
            String value = blankToNull(matcher.group(2));
            switch (key) {
                case "name" -> name = value;
                case "description" -> description = value;
                case "state" -> state = value;
                case "ssid" -> ssid = value;
                case "bssid" -> bssid = value;
                case "radio type" -> radioType = value;
                case "authentication" -> authentication = value;
                case "channel" -> channel = value;
                case "receive rate (mbps)" -> receiveRate = value;
                case "transmit rate (mbps)" -> transmitRate = value;
                case "signal" -> signal = parseSignal(value);
                case "profile" -> profile = value;
                default -> {
                    // Ignore fields that are not used for monitoring.
                }
            }
        }
        return new WlanInterfaceInfo(
                name,
                description,
                state,
                ssid,
                bssid,
                radioType,
                authentication,
                channel,
                receiveRate,
                transmitRate,
                signal,
                profile,
                output);
    }

    private Integer parseSignal(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = SIGNAL.matcher(value);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private WlanInterfaceInfo empty(String output) {
        return new WlanInterfaceInfo(
                null, null, "unknown", null, null, null, null, null, null, null, null, null, output);
    }
}
