package com.crimson.wifimonitor.dto;

import com.crimson.wifimonitor.domain.enums.EventType;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WifiDiagnosticResponse {
    Long snapshotId;
    Long eventId;
    Instant capturedAt;
    EventType triggerEventType;
    String adapterState;
    String radioType;
    String authentication;
    String bssid;
    String channel;
    String receiveRateMbps;
    String transmitRateMbps;
    String netshInterfacesOutput;
    String wlanAutoconfigEvents;
    String wlanReportPath;
    String inferredCauses;
}
