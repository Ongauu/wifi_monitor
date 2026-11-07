package com.crimson.wifimonitor.dto;

import com.crimson.wifimonitor.domain.enums.DnsStatus;
import com.crimson.wifimonitor.domain.enums.FailureCategory;
import com.crimson.wifimonitor.domain.enums.GatewayStatus;
import com.crimson.wifimonitor.domain.enums.InternetStatus;
import com.crimson.wifimonitor.domain.enums.WifiStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WifiStatusResponse {
    Instant capturedAt;
    WifiStatus wifiStatus;
    InternetStatus internetStatus;
    GatewayStatus gatewayStatus;
    DnsStatus dnsStatus;
    FailureCategory primaryFailureCategory;
    String ssid;
    String interfaceName;
    String interfaceState;
    Integer signalStrengthPercent;
    String ipAddress;
    String gatewayAddress;
    Double gatewayLatencyMs;
    Double internetLatencyMs;
    Double packetLossPercent;
    Double connectionUptimeMinutes;
    Double currentDisconnectionDurationMinutes;
    String notes;
}
