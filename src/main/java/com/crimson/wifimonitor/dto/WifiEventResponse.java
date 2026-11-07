package com.crimson.wifimonitor.dto;

import com.crimson.wifimonitor.domain.enums.DnsStatus;
import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.domain.enums.FailureCategory;
import com.crimson.wifimonitor.domain.enums.GatewayStatus;
import com.crimson.wifimonitor.domain.enums.InternetStatus;
import com.crimson.wifimonitor.domain.enums.WifiStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WifiEventResponse {
    Long eventId;
    EventType eventType;
    Instant timestamp;
    String ssid;
    Integer signalStrength;
    String ipAddress;
    GatewayStatus gatewayStatus;
    InternetStatus internetStatus;
    DnsStatus dnsStatus;
    WifiStatus wifiStatus;
    FailureCategory failureCategory;
    String gatewayAddress;
    Double gatewayLatencyMs;
    Double internetLatencyMs;
    Double packetLossPercent;
    Double connectionUptimeMinutes;
    Double disconnectionDurationMinutes;
    String diagnosticInformation;
    Long sessionId;
}
