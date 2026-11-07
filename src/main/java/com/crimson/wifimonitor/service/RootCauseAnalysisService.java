package com.crimson.wifimonitor.service;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import com.crimson.wifimonitor.domain.entity.WifiConnectionEvent;
import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.dto.RootCauseFindingResponse;
import com.crimson.wifimonitor.repository.WifiConnectionEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RootCauseAnalysisService {

    private final WifiConnectionEventRepository eventRepository;
    private final WifiMonitorProperties properties;

    @Transactional(readOnly = true)
    public List<RootCauseFindingResponse> analyze(Instant from, Instant to) {
        List<WifiConnectionEvent> events =
                eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(from).stream()
                        .filter(event -> !event.getOccurredAt().isAfter(to))
                        .toList();
        List<RootCauseFindingResponse> findings = new ArrayList<>();
        findings.addAll(weakSignalDisconnects(events));
        findings.addAll(adapterDropsWithoutPriorInternetLoss(events));
        findings.addAll(internetLossWhileWifiConnected(events));
        findings.addAll(dnsFailureWithGatewayUp(events));
        findings.addAll(regularIntervalDisconnects(events));
        if (findings.isEmpty()) {
            findings.add(RootCauseFindingResponse.builder()
                    .observedPattern("No strong failure pattern yet")
                    .evidence("Not enough distinct failure transitions were recorded in the selected window.")
                    .possibleExplanation(
                            "The monitor may not have captured a disconnect cycle yet, or failures are too infrequent to classify.")
                    .confidence("LOW")
                    .recommendedSteps(List.of(
                            "Leave the application running through at least one disconnect cycle",
                            "Review GET /api/wifi/events after the next drop",
                            "Compare signal, gateway, DNS, and adapter status on the disconnect event"))
                    .build());
        }
        return findings;
    }

    public List<RootCauseFindingResponse> analyzeDefaultWindow() {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(properties.getAnalysisLookbackHours()));
        return analyze(from, to);
    }

    private List<RootCauseFindingResponse> weakSignalDisconnects(List<WifiConnectionEvent> events) {
        List<WifiConnectionEvent> disconnects = ofType(events, EventType.WIFI_DISCONNECTED);
        if (disconnects.size() < 3) {
            return List.of();
        }
        long weak = disconnects.stream()
                .filter(e -> e.getSignalStrengthPercent() != null
                        && e.getSignalStrengthPercent() <= properties.getSignalLowThresholdPercent())
                .count();
        double ratio = weak / (double) disconnects.size();
        if (ratio < 0.5) {
            return List.of();
        }
        return List.of(RootCauseFindingResponse.builder()
                .observedPattern("Frequent Wi-Fi adapter disconnections while signal strength is low")
                .evidence(weak + " of " + disconnects.size() + " WIFI_DISCONNECTED events had signal at or below "
                        + properties.getSignalLowThresholdPercent() + "%.")
                .possibleExplanation(
                        "The radio may be dropping association because of distance, obstruction, interference, or roaming.")
                .confidence(confidence(disconnects.size(), ratio))
                .recommendedSteps(List.of(
                        "Move closer to the access point and compare later sessions",
                        "Check for microwave/Bluetooth interference on 2.4 GHz",
                        "Review access point band steering and roaming aggressiveness",
                        "Update the Wi-Fi adapter driver from the vendor"))
                .build());
    }

    private List<RootCauseFindingResponse> adapterDropsWithoutPriorInternetLoss(List<WifiConnectionEvent> events) {
        List<WifiConnectionEvent> disconnects = ofType(events, EventType.WIFI_DISCONNECTED);
        if (disconnects.size() < 3) {
            return List.of();
        }
        long withoutPrior = 0;
        for (WifiConnectionEvent disconnect : disconnects) {
            boolean priorInternetLost = events.stream().anyMatch(event -> event.getEventType() == EventType.INTERNET_LOST
                    && event.getOccurredAt().isBefore(disconnect.getOccurredAt())
                    && Duration.between(event.getOccurredAt(), disconnect.getOccurredAt()).toSeconds() <= 30);
            if (!priorInternetLost) {
                withoutPrior++;
            }
        }
        double ratio = withoutPrior / (double) disconnects.size();
        if (ratio < 0.6) {
            return List.of();
        }
        return List.of(RootCauseFindingResponse.builder()
                .observedPattern("Wi-Fi adapter disconnects without a preceding internet failure")
                .evidence(withoutPrior + " of " + disconnects.size()
                        + " adapter disconnects had no INTERNET_LOST event in the previous 30 seconds.")
                .possibleExplanation(
                        "The drop is likely at the wireless association layer (adapter, driver, power management, or AP kick) rather than a WAN outage.")
                .confidence(confidence(disconnects.size(), ratio))
                .recommendedSteps(List.of(
                        "Inspect GET /api/wifi/diagnostics for WLAN AutoConfig authentication/driver text",
                        "Disable Wi-Fi adapter power saving in Device Manager",
                        "Compare AP logs for deauth/disassoc at the same timestamps",
                        "Test with a USB Wi-Fi adapter if the onboard NIC resets"))
                .build());
    }

    private List<RootCauseFindingResponse> internetLossWhileWifiConnected(List<WifiConnectionEvent> events) {
        List<WifiConnectionEvent> losses = ofType(events, EventType.INTERNET_LOST);
        if (losses.size() < 2) {
            return List.of();
        }
        long whileConnected = losses.stream()
                .filter(e -> e.getWifiStatus() != null && e.getWifiStatus().name().equals("CONNECTED"))
                .count();
        if (whileConnected == 0) {
            return List.of();
        }
        double ratio = whileConnected / (double) losses.size();
        return List.of(RootCauseFindingResponse.builder()
                .observedPattern("Wi-Fi remains associated while internet connectivity disappears")
                .evidence(whileConnected + " of " + losses.size()
                        + " INTERNET_LOST events occurred while the adapter was still CONNECTED.")
                .possibleExplanation(
                        "The wireless link stayed up, so the issue is more likely upstream (router WAN, ISP, DNS, or gateway) than radio association.")
                .confidence(confidence(losses.size(), ratio))
                .recommendedSteps(List.of(
                        "Compare GATEWAY_UNREACHABLE vs INTERNET_LOST at the same times",
                        "Reboot the router only after confirming the gateway itself is failing",
                        "Check ISP modem/WAN lights against recorded timestamps",
                        "If the gateway stays reachable, focus on WAN/DNS rather than Wi-Fi"))
                .build());
    }

    private List<RootCauseFindingResponse> dnsFailureWithGatewayUp(List<WifiConnectionEvent> events) {
        List<WifiConnectionEvent> dnsFailures = ofType(events, EventType.DNS_FAILURE);
        if (dnsFailures.size() < 2) {
            return List.of();
        }
        long gatewayUp = dnsFailures.stream()
                .filter(e -> e.getGatewayStatus() != null && e.getGatewayStatus().name().equals("REACHABLE"))
                .count();
        if (gatewayUp == 0) {
            return List.of();
        }
        double ratio = gatewayUp / (double) dnsFailures.size();
        return List.of(RootCauseFindingResponse.builder()
                .observedPattern("DNS failures occur while the default gateway remains reachable")
                .evidence(gatewayUp + " of " + dnsFailures.size()
                        + " DNS_FAILURE events still had a reachable gateway.")
                .possibleExplanation(
                        "Name resolution is failing independently of local LAN connectivity. This can be a DNS server, resolver, or filtering issue.")
                .confidence(confidence(dnsFailures.size(), ratio))
                .recommendedSteps(List.of(
                        "Note whether internet ICMP still succeeded during those DNS_FAILURE events",
                        "Temporarily test a public resolver only as a diagnostic comparison",
                        "Inspect router DNS/DHCP settings",
                        "Look for security software intercepting DNS"))
                .build());
    }

    private List<RootCauseFindingResponse> regularIntervalDisconnects(List<WifiConnectionEvent> events) {
        List<Instant> times = ofType(events, EventType.WIFI_DISCONNECTED).stream()
                .map(WifiConnectionEvent::getOccurredAt)
                .sorted()
                .toList();
        if (times.size() < 5) {
            return List.of();
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            gaps.add(Duration.between(times.get(i - 1), times.get(i)).toSeconds());
        }
        double avg = gaps.stream().mapToLong(Long::longValue).average().orElse(0);
        if (avg < 30) {
            return List.of();
        }
        double variance = gaps.stream().mapToDouble(g -> Math.pow(g - avg, 2)).average().orElse(0);
        double stddev = Math.sqrt(variance);
        if (stddev > avg * 0.25) {
            return List.of();
        }
        return List.of(RootCauseFindingResponse.builder()
                .observedPattern("Connection failures occur at a fairly regular interval")
                .evidence("WIFI_DISCONNECTED gaps averaged "
                        + roundMinutes(avg) + " minutes with a standard deviation of "
                        + roundMinutes(stddev) + " minutes across " + gaps.size() + " intervals.")
                .possibleExplanation(
                        "A scheduled action may be involved: adapter power saving, VPN reconnect, AP client timeout, or a recurring scan/reset.")
                .confidence(times.size() >= 8 ? "MEDIUM" : "LOW")
                .recommendedSteps(List.of(
                        "Compare the interval with Windows power and WLAN AutoConfig event times",
                        "Disable scheduled network troubleshooting tools temporarily",
                        "Check AP idle-timeout / session-timeout values",
                        "Note whether the interval matches a VPN or security client reconnect"))
                .build());
    }

    private List<WifiConnectionEvent> ofType(List<WifiConnectionEvent> events, EventType type) {
        return events.stream()
                .filter(event -> event.getEventType() == type)
                .sorted(Comparator.comparing(WifiConnectionEvent::getOccurredAt))
                .toList();
    }

    private static double roundMinutes(double seconds) {
        return Math.round((seconds / 60.0) * 100.0) / 100.0;
    }

    private String confidence(long samples, double ratio) {
        if (samples >= 8 && ratio >= 0.75) {
            return "HIGH";
        }
        if (samples >= 4 && ratio >= 0.6) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
