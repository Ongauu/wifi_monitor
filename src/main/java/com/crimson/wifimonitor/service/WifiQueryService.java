package com.crimson.wifimonitor.service;

import com.crimson.wifimonitor.domain.entity.WifiConnectionEvent;
import com.crimson.wifimonitor.domain.entity.WifiConnectionSession;
import com.crimson.wifimonitor.domain.entity.WifiDiagnosticSnapshot;
import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.dto.WifiDiagnosticResponse;
import com.crimson.wifimonitor.dto.WifiEventResponse;
import com.crimson.wifimonitor.dto.WifiSessionResponse;
import com.crimson.wifimonitor.dto.WifiStatusResponse;
import com.crimson.wifimonitor.repository.WifiConnectionEventRepository;
import com.crimson.wifimonitor.repository.WifiConnectionSessionRepository;
import com.crimson.wifimonitor.repository.WifiDiagnosticSnapshotRepository;
import com.crimson.wifimonitor.service.model.NetworkObservation;
import com.crimson.wifimonitor.util.DurationMinutes;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WifiQueryService {

    private final WifiMonitorService wifiMonitorService;
    private final WifiConnectionEventRepository eventRepository;
    private final WifiConnectionSessionRepository sessionRepository;
    private final WifiDiagnosticSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public WifiStatusResponse currentStatus() {
        NetworkObservation observation = wifiMonitorService.currentObservation();
        WifiMonitorService.MonitorRuntimeState runtime = wifiMonitorService.runtimeState();
        Long uptime = null;
        Long downtime = null;
        if (observation.wifiConnected() && runtime.sessionStartedAt() != null) {
            uptime = Duration.between(runtime.sessionStartedAt(), observation.getCapturedAt()).toMillis();
        }
        if (!observation.wifiConnected() && runtime.disconnectStartedAt() != null) {
            downtime = Duration.between(runtime.disconnectStartedAt(), Instant.now()).toMillis();
        }
        return WifiStatusResponse.builder()
                .capturedAt(observation.getCapturedAt())
                .wifiStatus(observation.getWifiStatus())
                .internetStatus(observation.getInternetStatus())
                .gatewayStatus(observation.getGatewayStatus())
                .dnsStatus(observation.getDnsStatus())
                .primaryFailureCategory(observation.primaryFailureCategory())
                .ssid(observation.getSsid())
                .interfaceName(observation.getInterfaceName())
                .interfaceState(observation.getInterfaceState())
                .signalStrengthPercent(observation.getSignalPercent())
                .ipAddress(observation.getIpAddress())
                .gatewayAddress(observation.getGatewayAddress())
                .gatewayLatencyMs(observation.getGatewayLatencyMs())
                .internetLatencyMs(observation.getInternetLatencyMs())
                .packetLossPercent(observation.getPacketLossPercent())
                .connectionUptimeMinutes(DurationMinutes.fromMillis(uptime))
                .currentDisconnectionDurationMinutes(DurationMinutes.fromMillis(downtime))
                .notes(observation.getCollectionNotes())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<WifiEventResponse> events(EventType type, Instant from, Instant to, Pageable pageable) {
        Page<WifiConnectionEvent> page = type == null
                ? eventRepository.findByOccurredAtBetween(from, to, pageable)
                : eventRepository.findByEventTypeAndOccurredAtBetween(type, from, to, pageable);
        return page.map(this::toEvent);
    }

    @Transactional(readOnly = true)
    public Page<WifiSessionResponse> sessions(Instant from, Instant to, Pageable pageable) {
        return sessionRepository.findByStartedAtBetween(from, to, pageable).map(this::toSession);
    }

    @Transactional(readOnly = true)
    public Page<WifiDiagnosticResponse> diagnostics(Instant from, Instant to, Pageable pageable) {
        return snapshotRepository.findByCapturedAtBetween(from, to, pageable).map(this::toDiagnostic);
    }

    private WifiEventResponse toEvent(WifiConnectionEvent event) {
        return WifiEventResponse.builder()
                .eventId(event.getId())
                .eventType(event.getEventType())
                .timestamp(event.getOccurredAt())
                .ssid(event.getSsid())
                .signalStrength(event.getSignalStrengthPercent())
                .ipAddress(event.getIpAddress())
                .gatewayStatus(event.getGatewayStatus())
                .internetStatus(event.getInternetStatus())
                .dnsStatus(event.getDnsStatus())
                .wifiStatus(event.getWifiStatus())
                .failureCategory(event.getFailureCategory())
                .gatewayAddress(event.getGatewayAddress())
                .gatewayLatencyMs(event.getGatewayLatencyMs())
                .internetLatencyMs(event.getInternetLatencyMs())
                .packetLossPercent(event.getPacketLossPercent())
                .connectionUptimeMinutes(DurationMinutes.fromMillis(event.getConnectionUptimeMs()))
                .disconnectionDurationMinutes(DurationMinutes.fromMillis(event.getDisconnectionDurationMs()))
                .diagnosticInformation(event.getDiagnosticSummary())
                .sessionId(event.getSession() != null ? event.getSession().getId() : null)
                .build();
    }

    private WifiSessionResponse toSession(WifiConnectionSession session) {
        Long durationMs = session.getDurationMs();
        if (durationMs == null && session.getEndedAt() == null && session.getStartedAt() != null) {
            durationMs = Duration.between(session.getStartedAt(), Instant.now()).toMillis();
        }
        return WifiSessionResponse.builder()
                .sessionId(session.getId())
                .ssid(session.getSsid())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .durationMinutes(DurationMinutes.fromMillis(durationMs))
                .startIpAddress(session.getStartIpAddress())
                .endIpAddress(session.getEndIpAddress())
                .startSignalPercent(session.getStartSignalPercent())
                .endSignalPercent(session.getEndSignalPercent())
                .disconnectReason(session.getDisconnectReason())
                .eventCount(session.getEventCount())
                .build();
    }

    private WifiDiagnosticResponse toDiagnostic(WifiDiagnosticSnapshot snapshot) {
        return WifiDiagnosticResponse.builder()
                .snapshotId(snapshot.getId())
                .eventId(snapshot.getEvent() != null ? snapshot.getEvent().getId() : null)
                .capturedAt(snapshot.getCapturedAt())
                .triggerEventType(snapshot.getTriggerEventType())
                .adapterState(snapshot.getAdapterState())
                .radioType(snapshot.getRadioType())
                .authentication(snapshot.getAuthentication())
                .bssid(snapshot.getBssid())
                .channel(snapshot.getChannel())
                .receiveRateMbps(snapshot.getReceiveRateMbps())
                .transmitRateMbps(snapshot.getTransmitRateMbps())
                .netshInterfacesOutput(snapshot.getNetshInterfacesOutput())
                .wlanAutoconfigEvents(snapshot.getWlanAutoconfigEvents())
                .wlanReportPath(snapshot.getWlanReportPath())
                .inferredCauses(snapshot.getInferredCauses())
                .build();
    }
}
