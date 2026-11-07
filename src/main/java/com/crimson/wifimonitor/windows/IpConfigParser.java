package com.crimson.wifimonitor.windows;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class IpConfigParser {

    public AdapterAddresses parse(String output, String interfaceName) {
        if (output == null || output.isBlank()) {
            return AdapterAddresses.empty();
        }

        String target = interfaceName == null ? "wireless" : interfaceName.toLowerCase(Locale.ROOT);
        StringBuilder current = new StringBuilder();
        String bestBlock = null;
        int bestScore = -1;
        String currentHeader = "";
        for (String line : output.split("\\R", -1)) {
            if (!line.startsWith(" ") && !line.isBlank() && line.toLowerCase(Locale.ROOT).contains("adapter")) {
                if (!current.isEmpty()) {
                    int score = score(currentHeader, current.toString(), target);
                    if (score > bestScore) {
                        bestScore = score;
                        bestBlock = current.toString();
                    }
                }
                currentHeader = line;
                current = new StringBuilder(line).append('\n');
            } else {
                current.append(line).append('\n');
            }
        }
        if (!current.isEmpty()) {
            int score = score(currentHeader, current.toString(), target);
            if (score > bestScore) {
                bestBlock = current.toString();
            }
        }

        String block = bestBlock == null ? output : bestBlock;
        return new AdapterAddresses(extract(block, "IPv4 Address"), extractGateway(block), block);
    }

    private int score(String header, String body, String target) {
        String combined = (header + " " + body).toLowerCase(Locale.ROOT);
        int score = 0;
        if (combined.contains("wi-fi") || combined.contains("wifi") || combined.contains("wireless")) {
            score += 5;
        }
        if (target != null && combined.contains(target.toLowerCase(Locale.ROOT))) {
            score += 8;
        }
        if (combined.contains("ipv4 address") && extractGateway(body) != null) {
            score += 2;
        }
        return score;
    }

    private String extract(String block, String label) {
        for (String line : block.split("\\R")) {
            if (line.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))) {
                int idx = line.indexOf(':');
                if (idx >= 0) {
                    String value = line.substring(idx + 1).trim();
                    value = value.replace("(Preferred)", "").trim();
                    return value.isEmpty() ? null : value;
                }
            }
        }
        return null;
    }

    private String extractGateway(String block) {
        String gateway = extract(block, "Default Gateway");
        if (gateway == null || gateway.isBlank() || gateway.startsWith("fe80")) {
            return gateway != null && !gateway.startsWith("fe80") ? gateway : null;
        }
        return gateway;
    }

    public record AdapterAddresses(String ipv4, String gateway, String rawBlock) {
        public static AdapterAddresses empty() {
            return new AdapterAddresses(null, null, null);
        }
    }
}
