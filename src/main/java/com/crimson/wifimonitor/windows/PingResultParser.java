package com.crimson.wifimonitor.windows;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PingResultParser {

    private static final Pattern LOSS = Pattern.compile("Lost\\s*=\\s*\\d+\\s*\\((\\d+)%\\s*loss\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AVERAGE = Pattern.compile("Average\\s*=\\s*(\\d+)ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECEIVED = Pattern.compile("Received\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public PingStats parse(String output) {
        if (output == null || output.isBlank()) {
            return PingStats.failed();
        }
        Double loss = firstDouble(LOSS, output);
        Double average = firstDouble(AVERAGE, output);
        Integer received = firstInt(RECEIVED, output);
        boolean reachable = received != null && received > 0 && (loss == null || loss < 100);
        String lower = output.toLowerCase();
        if (lower.contains("destination host unreachable")
                || (lower.contains("request timed out") && (received == null || received == 0))) {
            reachable = received != null && received > 0;
        }
        return new PingStats(reachable, average, loss);
    }

    private Double firstDouble(Pattern pattern, String output) {
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private Integer firstInt(Pattern pattern, String output) {
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    public record PingStats(boolean reachable, Double averageLatencyMs, Double packetLossPercent) {
        public static PingStats failed() {
            return new PingStats(false, null, 100.0);
        }
    }
}
