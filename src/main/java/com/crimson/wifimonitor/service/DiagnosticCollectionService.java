package com.crimson.wifimonitor.service;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import com.crimson.wifimonitor.domain.entity.WifiConnectionEvent;
import com.crimson.wifimonitor.domain.entity.WifiDiagnosticSnapshot;
import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.repository.WifiConnectionEventRepository;
import com.crimson.wifimonitor.repository.WifiDiagnosticSnapshotRepository;
import com.crimson.wifimonitor.service.model.NetworkObservation;
import com.crimson.wifimonitor.windows.CommandResult;
import com.crimson.wifimonitor.windows.WlanEventAnalyzer;
import com.crimson.wifimonitor.windows.WindowsCommandExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticCollectionService {

    private static final Path WLAN_REPORT =
            Path.of("C:\\ProgramData\\Microsoft\\Windows\\WlanReport\\wlan-report-latest.html");

    private final WindowsCommandExecutor commandExecutor;
    private final WlanEventAnalyzer wlanEventAnalyzer;
    private final WifiDiagnosticSnapshotRepository snapshotRepository;
    private final WifiConnectionEventRepository eventRepository;
    private final WifiMonitorProperties properties;

    @Async("wifiDiagnosticExecutor")
    @Transactional
    public void collectForDisconnect(Long eventId, NetworkObservation observation) {
        try {
            WifiConnectionEvent event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                log.warn("Skipping diagnostics; event {} was not found", eventId);
                return;
            }
            WifiDiagnosticSnapshot snapshot = new WifiDiagnosticSnapshot();
            snapshot.setEvent(event);
            snapshot.setCapturedAt(Instant.now());
            snapshot.setTriggerEventType(event.getEventType());
            snapshot.setAdapterState(observation.getInterfaceState());
            snapshot.setRadioType(observation.getRadioType());
            snapshot.setAuthentication(observation.getAuthentication());
            snapshot.setBssid(observation.getBssid());
            snapshot.setChannel(observation.getChannel());
            snapshot.setReceiveRateMbps(observation.getReceiveRateMbps());
            snapshot.setTransmitRateMbps(observation.getTransmitRateMbps());
            snapshot.setNetshInterfacesOutput(observation.getNetshOutput());

            List<String> inferred = new ArrayList<>();
            if (properties.isCollectWlanEventsOnDisconnect()) {
                CommandResult events = commandExecutor.execute(List.of(
                        "wevtutil",
                        "qe",
                        "Microsoft-Windows-WLAN-AutoConfig/Operational",
                        "/c:40",
                        "/rd:true",
                        "/f:text"));
                snapshot.setWlanAutoconfigEvents(events.output());
                inferred.addAll(wlanEventAnalyzer.inferCauses(events.output()));
                if (!events.succeeded()) {
                    inferred.add("WLAN AutoConfig event query failed or was unavailable");
                }
            }
            if (properties.isCollectWlanReportOnDisconnect() && canRunWlanReport()) {
                CommandResult report = commandExecutor.execute(
                        List.of("netsh", "wlan", "show", "wlanreport"), Duration.ofSeconds(60));
                if (Files.exists(WLAN_REPORT)) {
                    snapshot.setWlanReportPath(WLAN_REPORT.toString());
                }
                if (!report.succeeded()) {
                    inferred.add("wlanreport command failed or timed out; it was not retried automatically");
                }
            }
            if (observation.getSignalPercent() != null
                    && observation.getSignalPercent() <= properties.getSignalLowThresholdPercent()) {
                inferred.add("Signal was already weak at disconnect time (" + observation.getSignalPercent() + "%)");
            }
            snapshot.setInferredCauses(String.join("\n", inferred));
            snapshotRepository.save(snapshot);
        } catch (Exception ex) {
            log.warn("Diagnostic collection failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public void collectLightweightSnapshot(WifiConnectionEvent event, NetworkObservation observation) {
        if (event.getEventType() != EventType.MONITORING_STARTED) {
            return;
        }
        WifiDiagnosticSnapshot snapshot = new WifiDiagnosticSnapshot();
        snapshot.setEvent(event);
        snapshot.setCapturedAt(observation.getCapturedAt());
        snapshot.setTriggerEventType(event.getEventType());
        snapshot.setAdapterState(observation.getInterfaceState());
        snapshot.setRadioType(observation.getRadioType());
        snapshot.setAuthentication(observation.getAuthentication());
        snapshot.setBssid(observation.getBssid());
        snapshot.setChannel(observation.getChannel());
        snapshot.setReceiveRateMbps(observation.getReceiveRateMbps());
        snapshot.setTransmitRateMbps(observation.getTransmitRateMbps());
        snapshot.setNetshInterfacesOutput(observation.getNetshOutput());
        snapshotRepository.save(snapshot);
    }

    private boolean canRunWlanReport() {
        return snapshotRepository
                .findTopByWlanReportPathIsNotNullOrderByCapturedAtDesc()
                .map(existing -> Duration.between(existing.getCapturedAt(), Instant.now()).toMinutes()
                        >= properties.getWlanReportMinIntervalMinutes())
                .orElse(true);
    }
}
