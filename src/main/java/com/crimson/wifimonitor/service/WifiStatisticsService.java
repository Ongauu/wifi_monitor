package com.crimson.wifimonitor.service;

import com.crimson.wifimonitor.domain.entity.WifiConnectionEvent;
import com.crimson.wifimonitor.domain.entity.WifiConnectionSession;
import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.domain.enums.InternetStatus;
import com.crimson.wifimonitor.domain.enums.SessionStatus;
import com.crimson.wifimonitor.dto.WifiStatisticsResponse;
import com.crimson.wifimonitor.repository.WifiConnectionEventRepository;
import com.crimson.wifimonitor.repository.WifiConnectionSessionRepository;
import com.crimson.wifimonitor.util.DurationMinutes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WifiStatisticsService {

    private final WifiConnectionEventRepository eventRepository;
    private final WifiConnectionSessionRepository sessionRepository;
    private final WifiMonitorService wifiMonitorService;

    @Transactional(readOnly = true)
    public WifiStatisticsResponse statistics(Instant from, Instant to) {
        List<WifiConnectionEvent> events =
                eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(from).stream()
                        .filter(event -> !event.getOccurredAt().isAfter(to))
                        .toList();
        List<WifiConnectionSession> sessions =
                sessionRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(from).stream()
                        .filter(session -> !session.getStartedAt().isAfter(to))
                        .toList();

        long wifiDisconnects = events.stream().filter(e -> e.getEventType() == EventType.WIFI_DISCONNECTED).count();
        long internetLosses = events.stream().filter(e -> e.getEventType() == EventType.INTERNET_LOST).count();
        long dnsFailures = events.stream().filter(e -> e.getEventType() == EventType.DNS_FAILURE).count();
        long gatewayFailures = events.stream().filter(e -> e.getEventType() == EventType.GATEWAY_UNREACHABLE).count();

        long closedDuration = sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED && s.getDurationMs() != null)
                .mapToLong(WifiConnectionSession::getDurationMs)
                .sum();
        long openDuration = sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.OPEN)
                .mapToLong(s -> Duration.between(s.getStartedAt(), Instant.now()).toMillis())
                .sum();
        long totalConnectionTime = closedDuration + openDuration;

        List<Long> disconnectDurations = events.stream()
                .filter(e -> e.getEventType() == EventType.WIFI_RECONNECTED
                        || e.getEventType() == EventType.WIFI_CONNECTED)
                .map(WifiConnectionEvent::getDisconnectionDurationMs)
                .filter(v -> v != null && v > 0)
                .toList();
        long totalDowntime = disconnectDurations.stream().mapToLong(Long::longValue).sum();
        if (wifiMonitorService.runtimeState().disconnectStartedAt() != null
                && wifiMonitorService.currentObservation() != null
                && !wifiMonitorService.currentObservation().wifiConnected()) {
            totalDowntime += Duration.between(
                            wifiMonitorService.runtimeState().disconnectStartedAt(), Instant.now())
                    .toMillis();
        }

        Double avgConnection = sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED && s.getDurationMs() != null)
                .mapToLong(WifiConnectionSession::getDurationMs)
                .average()
                .orElse(Double.NaN);
        Double avgDisconnect = disconnectDurations.stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
        Long longestDisconnect = disconnectDurations.stream().max(Long::compareTo).orElse(null);
        Double avgSignal = eventRepository.averageSignalBetween(from, to);

        double windowMs = Math.max(1, Duration.between(from, to).toMillis());
        long internetUnavailableMs = estimateInternetUnavailableMs(events, from, to);
        double internetAvailability = Math.max(0, Math.min(100, 100.0 - (internetUnavailableMs / windowMs) * 100.0));

        return WifiStatisticsResponse.builder()
                .from(from)
                .to(to)
                .totalWifiDisconnections(wifiDisconnects)
                .totalInternetLossEvents(internetLosses)
                .totalDnsFailures(dnsFailures)
                .totalGatewayFailures(gatewayFailures)
                .totalConnectionTimeMinutes(DurationMinutes.fromMillis(totalConnectionTime))
                .totalDowntimeMinutes(DurationMinutes.fromMillis(totalDowntime))
                .averageConnectionDurationMinutes(
                        Double.isNaN(avgConnection) ? null : DurationMinutes.fromMillis(avgConnection))
                .averageDisconnectionDurationMinutes(
                        Double.isNaN(avgDisconnect) ? null : DurationMinutes.fromMillis(avgDisconnect))
                .longestDisconnectionMinutes(DurationMinutes.fromMillis(longestDisconnect))
                .averageSignalStrengthPercent(avgSignal)
                .internetAvailabilityPercent(internetAvailability)
                .build();
    }

    private long estimateInternetUnavailableMs(List<WifiConnectionEvent> events, Instant from, Instant to) {
        Instant lostAt = null;
        long total = 0;
        for (WifiConnectionEvent event : events) {
            if (event.getEventType() == EventType.INTERNET_LOST) {
                lostAt = event.getOccurredAt();
            } else if (event.getEventType() == EventType.INTERNET_RESTORED && lostAt != null) {
                total += Duration.between(lostAt, event.getOccurredAt()).toMillis();
                lostAt = null;
            } else if (event.getEventType() == EventType.WIFI_DISCONNECTED && lostAt != null) {
                total += Duration.between(lostAt, event.getOccurredAt()).toMillis();
                lostAt = null;
            }
        }
        if (lostAt != null) {
            total += Duration.between(lostAt, to).toMillis();
        }
        if (events.isEmpty()) {
            return wifiMonitorService.currentObservation() != null
                            && wifiMonitorService.currentObservation().getInternetStatus()
                                    == InternetStatus.UNAVAILABLE
                    ? Duration.between(from, to).toMillis()
                    : 0;
        }
        return total;
    }
}
