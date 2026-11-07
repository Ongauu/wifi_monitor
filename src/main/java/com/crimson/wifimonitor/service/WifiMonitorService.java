package com.crimson.wifimonitor.service;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import com.crimson.wifimonitor.domain.entity.WifiConnectionEvent;
import com.crimson.wifimonitor.domain.entity.WifiConnectionSession;
import com.crimson.wifimonitor.domain.enums.DnsStatus;
import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.domain.enums.FailureCategory;
import com.crimson.wifimonitor.domain.enums.GatewayStatus;
import com.crimson.wifimonitor.domain.enums.InternetStatus;
import com.crimson.wifimonitor.domain.enums.SessionStatus;
import com.crimson.wifimonitor.domain.enums.WifiStatus;
import com.crimson.wifimonitor.repository.WifiConnectionEventRepository;
import com.crimson.wifimonitor.repository.WifiConnectionSessionRepository;
import com.crimson.wifimonitor.service.model.NetworkObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class WifiMonitorService {

    private final WifiStatusCollector collector;
    private final WifiConnectionEventRepository eventRepository;
    private final WifiConnectionSessionRepository sessionRepository;
    private final DiagnosticCollectionService diagnosticCollectionService;
    private final WifiMonitorProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<MonitorRuntimeState> runtime = new AtomicReference<>(MonitorRuntimeState.initial());

    @Transactional
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("Skipping poll because the previous cycle is still running");
            return;
        }
        try {
            NetworkObservation observation = collector.collect();
            processObservation(observation);
            runtime.get().lastObservation = observation;
        } catch (Exception ex) {
            log.error("Wi-Fi monitoring poll failed", ex);
        } finally {
            running.set(false);
        }
    }

    private void processObservation(NetworkObservation observation) {
        MonitorRuntimeState state = runtime.get();
        if (!state.initialized) {
            initializeFromDatabase(observation);
            state = runtime.get();
        }

        List<EventType> transitions = detectTransitions(state, observation);
        WifiConnectionSession session = ensureSession(state, observation, transitions);

        Instant disconnectStartedAt = state.disconnectStartedAt;
        Instant internetLostAt = state.internetLostAt;
        boolean signalLowLatched = state.signalLowLatched;

        WifiConnectionEvent lastSaved = null;
        for (EventType type : transitions) {
            Long disconnectionDuration = null;
            if (type == EventType.WIFI_CONNECTED || type == EventType.WIFI_RECONNECTED) {
                if (disconnectStartedAt != null) {
                    disconnectionDuration = Duration.between(disconnectStartedAt, observation.getCapturedAt()).toMillis();
                }
            }
            if (type == EventType.INTERNET_RESTORED && internetLostAt != null) {
                disconnectionDuration = Duration.between(internetLostAt, observation.getCapturedAt()).toMillis();
            }

            WifiConnectionEvent event = toEvent(observation, type, session, disconnectionDuration, state.sessionStartedAt);
            lastSaved = eventRepository.save(event);
            if (session != null) {
                session.setEventCount(session.getEventCount() + 1);
                sessionRepository.save(session);
            }
            log.info("Recorded {} ssid={} wifi={} internet={} dns={} gateway={} signal={}",
                    type,
                    observation.getSsid(),
                    observation.getWifiStatus(),
                    observation.getInternetStatus(),
                    observation.getDnsStatus(),
                    observation.getGatewayStatus(),
                    observation.getSignalPercent());
        }

        if (transitions.contains(EventType.WIFI_DISCONNECTED) && lastSaved != null) {
            Long eventId = lastSaved.getId();
            Runnable collect = () -> diagnosticCollectionService.collectForDisconnect(eventId, observation);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        collect.run();
                    }
                });
            } else {
                collect.run();
            }
        }

        if (transitions.contains(EventType.WIFI_DISCONNECTED)) {
            disconnectStartedAt = observation.getCapturedAt();
        }
        if (transitions.contains(EventType.WIFI_CONNECTED) || transitions.contains(EventType.WIFI_RECONNECTED)) {
            disconnectStartedAt = null;
        }
        if (transitions.contains(EventType.INTERNET_LOST)) {
            internetLostAt = observation.getCapturedAt();
        }
        if (transitions.contains(EventType.INTERNET_RESTORED)) {
            internetLostAt = null;
        }
        if (transitions.contains(EventType.SIGNAL_STRENGTH_LOW)) {
            signalLowLatched = true;
        }
        if (transitions.contains(EventType.SIGNAL_STRENGTH_RECOVERED)) {
            signalLowLatched = false;
        }

        Instant sessionStartedAt = session != null && session.getStatus() == SessionStatus.OPEN
                ? session.getStartedAt()
                : null;
        runtime.set(new MonitorRuntimeState(
                true,
                true,
                observation.getWifiStatus(),
                observation.getInternetStatus(),
                observation.getGatewayStatus(),
                observation.getDnsStatus(),
                signalLowLatched,
                disconnectStartedAt,
                internetLostAt,
                sessionStartedAt,
                session != null ? session.getId() : null,
                observation));
    }

    private void initializeFromDatabase(NetworkObservation observation) {
        Optional<WifiConnectionEvent> lastEvent = eventRepository.findTopByOrderByOccurredAtDesc();
        Optional<WifiConnectionSession> openSession =
                sessionRepository.findFirstByStatusOrderByStartedAtDesc(SessionStatus.OPEN);

        WifiStatus previousWifi = lastEvent.map(WifiConnectionEvent::getWifiStatus).orElse(WifiStatus.UNKNOWN);
        InternetStatus previousInternet =
                lastEvent.map(WifiConnectionEvent::getInternetStatus).orElse(InternetStatus.UNKNOWN);
        GatewayStatus previousGateway =
                lastEvent.map(WifiConnectionEvent::getGatewayStatus).orElse(GatewayStatus.UNKNOWN);
        DnsStatus previousDns = lastEvent.map(WifiConnectionEvent::getDnsStatus).orElse(DnsStatus.UNKNOWN);

        WifiConnectionSession session = openSession.orElse(null);
        if (!observation.wifiConnected() && session != null) {
            closeSession(session, observation, FailureCategory.WIFI_ADAPTER_DISCONNECT);
            session = null;
        }

        WifiConnectionEvent started = toEvent(
                observation,
                EventType.MONITORING_STARTED,
                session,
                null,
                session != null ? session.getStartedAt() : null);
        eventRepository.save(started);
        diagnosticCollectionService.collectLightweightSnapshot(started, observation);

        runtime.set(new MonitorRuntimeState(
                true,
                false,
                previousWifi,
                previousInternet,
                previousGateway,
                previousDns,
                lastEvent
                        .map(event -> event.getEventType() == EventType.SIGNAL_STRENGTH_LOW)
                        .orElse(false),
                observation.wifiConnected() ? null : observation.getCapturedAt(),
                observation.getInternetStatus() == InternetStatus.UNAVAILABLE ? observation.getCapturedAt() : null,
                session != null ? session.getStartedAt() : null,
                session != null ? session.getId() : null,
                null));
        log.info("Monitoring started. Restored wifi={} internet={} openSession={}",
                previousWifi, previousInternet, session != null ? session.getId() : "none");
    }

    private List<EventType> detectTransitions(MonitorRuntimeState state, NetworkObservation current) {
        List<EventType> events = new ArrayList<>();
        if (!state.initializedComplete) {
            events.addAll(startupTransitions(state, current));
        } else {
            if (state.wifiStatus != WifiStatus.CONNECTED && current.wifiConnected()) {
                events.add(state.hadWifiBefore() ? EventType.WIFI_RECONNECTED : EventType.WIFI_CONNECTED);
            } else if (state.wifiStatus == WifiStatus.CONNECTED && !current.wifiConnected()) {
                events.add(EventType.WIFI_DISCONNECTED);
            }

            boolean wifiUp = current.wifiConnected();
            if (wifiUp && state.gatewayStatus != GatewayStatus.UNREACHABLE
                    && current.getGatewayStatus() == GatewayStatus.UNREACHABLE) {
                events.add(EventType.GATEWAY_UNREACHABLE);
            }
            if (wifiUp
                    && state.gatewayStatus == GatewayStatus.UNREACHABLE
                    && current.getGatewayStatus() == GatewayStatus.REACHABLE) {
                events.add(EventType.GATEWAY_RESTORED);
            }

            if (wifiUp && state.dnsStatus != DnsStatus.FAILED && current.getDnsStatus() == DnsStatus.FAILED) {
                events.add(EventType.DNS_FAILURE);
            }
            if (wifiUp && state.dnsStatus == DnsStatus.FAILED && current.getDnsStatus() == DnsStatus.RESOLVED) {
                events.add(EventType.DNS_RESTORED);
            }

            if (wifiUp
                    && state.internetStatus != InternetStatus.UNAVAILABLE
                    && current.getInternetStatus() == InternetStatus.UNAVAILABLE) {
                events.add(EventType.INTERNET_LOST);
            }
            if (wifiUp
                    && state.internetStatus == InternetStatus.UNAVAILABLE
                    && current.getInternetStatus() == InternetStatus.AVAILABLE) {
                events.add(EventType.INTERNET_RESTORED);
            }
        }

        Integer signal = current.getSignalPercent();
        if (current.wifiConnected() && signal != null) {
            if (!state.signalLowLatched && signal <= properties.getSignalLowThresholdPercent()) {
                events.add(EventType.SIGNAL_STRENGTH_LOW);
            } else if (state.signalLowLatched && signal >= properties.getSignalRecoverThresholdPercent()) {
                events.add(EventType.SIGNAL_STRENGTH_RECOVERED);
            }
        }
        return events;
    }

    private List<EventType> startupTransitions(MonitorRuntimeState state, NetworkObservation current) {
        List<EventType> events = new ArrayList<>();
        if (current.wifiConnected() && state.wifiStatus != WifiStatus.CONNECTED) {
            events.add(state.hadWifiBefore() ? EventType.WIFI_RECONNECTED : EventType.WIFI_CONNECTED);
        } else if (!current.wifiConnected() && state.wifiStatus == WifiStatus.CONNECTED) {
            events.add(EventType.WIFI_DISCONNECTED);
        }
        if (current.wifiConnected() && current.getInternetStatus() == InternetStatus.UNAVAILABLE) {
            events.add(EventType.INTERNET_LOST);
        }
        if (current.wifiConnected() && current.getGatewayStatus() == GatewayStatus.UNREACHABLE) {
            events.add(EventType.GATEWAY_UNREACHABLE);
        }
        if (current.wifiConnected() && current.getDnsStatus() == DnsStatus.FAILED) {
            events.add(EventType.DNS_FAILURE);
        }
        return events;
    }

    private WifiConnectionSession ensureSession(
            MonitorRuntimeState state, NetworkObservation observation, List<EventType> transitions) {
        WifiConnectionSession session = null;
        if (state.openSessionId != null) {
            session = sessionRepository.findById(state.openSessionId).orElse(null);
        }
        if (transitions.contains(EventType.WIFI_CONNECTED) || transitions.contains(EventType.WIFI_RECONNECTED)) {
            if (session != null && session.getStatus() == SessionStatus.OPEN) {
                closeSession(session, observation, FailureCategory.NONE);
            }
            session = openSession(observation);
        } else if (transitions.contains(EventType.WIFI_DISCONNECTED) && session != null) {
            closeSession(session, observation, FailureCategory.WIFI_ADAPTER_DISCONNECT);
        } else if (observation.wifiConnected() && (session == null || session.getStatus() != SessionStatus.OPEN)) {
            session = openSession(observation);
        }
        return session;
    }

    private WifiConnectionSession openSession(NetworkObservation observation) {
        WifiConnectionSession session = new WifiConnectionSession();
        session.setSsid(observation.getSsid());
        session.setStatus(SessionStatus.OPEN);
        session.setStartedAt(observation.getCapturedAt());
        session.setStartIpAddress(observation.getIpAddress());
        session.setStartSignalPercent(observation.getSignalPercent());
        session.setEventCount(0);
        return sessionRepository.save(session);
    }

    private void closeSession(
            WifiConnectionSession session, NetworkObservation observation, FailureCategory reason) {
        session.setStatus(SessionStatus.CLOSED);
        session.setEndedAt(observation.getCapturedAt());
        session.setDurationMs(Duration.between(session.getStartedAt(), observation.getCapturedAt()).toMillis());
        session.setEndIpAddress(observation.getIpAddress());
        session.setEndSignalPercent(observation.getSignalPercent());
        session.setDisconnectReason(reason);
        sessionRepository.save(session);
    }

    private WifiConnectionEvent toEvent(
            NetworkObservation observation,
            EventType type,
            WifiConnectionSession session,
            Long disconnectionDurationMs,
            Instant sessionStartedAt) {
        WifiConnectionEvent event = new WifiConnectionEvent();
        event.setSession(session);
        event.setEventType(type);
        event.setFailureCategory(categoryFor(type, observation));
        event.setOccurredAt(observation.getCapturedAt());
        event.setSsid(observation.getSsid());
        event.setSignalStrengthPercent(observation.getSignalPercent());
        event.setIpAddress(observation.getIpAddress());
        event.setInterfaceName(observation.getInterfaceName());
        event.setInterfaceState(observation.getInterfaceState());
        event.setWifiStatus(observation.getWifiStatus());
        event.setGatewayStatus(observation.getGatewayStatus());
        event.setInternetStatus(observation.getInternetStatus());
        event.setDnsStatus(observation.getDnsStatus());
        event.setGatewayAddress(observation.getGatewayAddress());
        event.setGatewayLatencyMs(observation.getGatewayLatencyMs());
        event.setInternetLatencyMs(observation.getInternetLatencyMs());
        event.setPacketLossPercent(observation.getPacketLossPercent());
        if (observation.wifiConnected() && sessionStartedAt != null) {
            event.setConnectionUptimeMs(Duration.between(sessionStartedAt, observation.getCapturedAt()).toMillis());
        }
        event.setDisconnectionDurationMs(disconnectionDurationMs);
        event.setDiagnosticSummary(buildSummary(observation, type, disconnectionDurationMs));
        return event;
    }

    private FailureCategory categoryFor(EventType type, NetworkObservation observation) {
        return switch (type) {
            case WIFI_DISCONNECTED -> FailureCategory.WIFI_ADAPTER_DISCONNECT;
            case INTERNET_LOST -> FailureCategory.INTERNET_PATH_FAILURE;
            case DNS_FAILURE -> FailureCategory.DNS_FAILURE;
            case GATEWAY_UNREACHABLE -> FailureCategory.GATEWAY_FAILURE;
            case SIGNAL_STRENGTH_LOW -> FailureCategory.WEAK_SIGNAL;
            default -> observation.primaryFailureCategory();
        };
    }

    private String buildSummary(NetworkObservation observation, EventType type, Long disconnectionDurationMs) {
        StringBuilder builder = new StringBuilder();
        builder.append(type)
                .append(". adapter=")
                .append(observation.getInterfaceState())
                .append(", ssid=")
                .append(observation.getSsid())
                .append(", ip=")
                .append(observation.getIpAddress())
                .append(", gateway=")
                .append(observation.getGatewayStatus())
                .append(", internet=")
                .append(observation.getInternetStatus())
                .append(", dns=")
                .append(observation.getDnsStatus());
        if (disconnectionDurationMs != null) {
            builder.append(", recoveredAfterMs=").append(disconnectionDurationMs);
        }
        if (observation.getCollectionNotes() != null && !observation.getCollectionNotes().isBlank()) {
            builder.append(". ").append(observation.getCollectionNotes());
        }
        return builder.toString();
    }

    public NetworkObservation currentObservation() {
        NetworkObservation cached = runtime.get().lastObservation;
        if (cached != null) {
            return cached;
        }
        return collector.collect();
    }

    public MonitorRuntimeState runtimeState() {
        return runtime.get();
    }

    public static final class MonitorRuntimeState {
        private final boolean initialized;
        private final boolean initializedComplete;
        private final WifiStatus wifiStatus;
        private final InternetStatus internetStatus;
        private final GatewayStatus gatewayStatus;
        private final DnsStatus dnsStatus;
        private final boolean signalLowLatched;
        private final Instant disconnectStartedAt;
        private final Instant internetLostAt;
        private final Instant sessionStartedAt;
        private final Long openSessionId;
        private NetworkObservation lastObservation;

        private MonitorRuntimeState(
                boolean initialized,
                boolean initializedComplete,
                WifiStatus wifiStatus,
                InternetStatus internetStatus,
                GatewayStatus gatewayStatus,
                DnsStatus dnsStatus,
                boolean signalLowLatched,
                Instant disconnectStartedAt,
                Instant internetLostAt,
                Instant sessionStartedAt,
                Long openSessionId,
                NetworkObservation lastObservation) {
            this.initialized = initialized;
            this.initializedComplete = initializedComplete;
            this.wifiStatus = wifiStatus;
            this.internetStatus = internetStatus;
            this.gatewayStatus = gatewayStatus;
            this.dnsStatus = dnsStatus;
            this.signalLowLatched = signalLowLatched;
            this.disconnectStartedAt = disconnectStartedAt;
            this.internetLostAt = internetLostAt;
            this.sessionStartedAt = sessionStartedAt;
            this.openSessionId = openSessionId;
            this.lastObservation = lastObservation;
        }

        private static MonitorRuntimeState initial() {
            return new MonitorRuntimeState(
                    false,
                    false,
                    WifiStatus.UNKNOWN,
                    InternetStatus.UNKNOWN,
                    GatewayStatus.UNKNOWN,
                    DnsStatus.UNKNOWN,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        private boolean hadWifiBefore() {
            return wifiStatus == WifiStatus.CONNECTED || wifiStatus == WifiStatus.DISCONNECTED;
        }

        public Instant disconnectStartedAt() {
            return disconnectStartedAt;
        }

        public Instant sessionStartedAt() {
            return sessionStartedAt;
        }
    }
}
