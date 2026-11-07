package com.crimson.wifimonitor.service.model;

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
public class NetworkObservation {
    Instant capturedAt;
    WifiStatus wifiStatus;
    InternetStatus internetStatus;
    GatewayStatus gatewayStatus;
    DnsStatus dnsStatus;
    String ssid;
    String interfaceName;
    String interfaceState;
    String adapterDescription;
    Integer signalPercent;
    String ipAddress;
    String gatewayAddress;
    Double gatewayLatencyMs;
    Double internetLatencyMs;
    Double packetLossPercent;
    String bssid;
    String radioType;
    String authentication;
    String channel;
    String receiveRateMbps;
    String transmitRateMbps;
    String netshOutput;
    String collectionNotes;

    public boolean wifiConnected() {
        return wifiStatus == WifiStatus.CONNECTED;
    }

    public FailureCategory primaryFailureCategory() {
        if (wifiStatus != WifiStatus.CONNECTED) {
            return FailureCategory.WIFI_ADAPTER_DISCONNECT;
        }
        if (gatewayStatus == GatewayStatus.UNREACHABLE) {
            return FailureCategory.GATEWAY_FAILURE;
        }
        if (dnsStatus == DnsStatus.FAILED && internetStatus == InternetStatus.UNAVAILABLE) {
            return FailureCategory.DNS_FAILURE;
        }
        if (dnsStatus == DnsStatus.FAILED) {
            return FailureCategory.DNS_FAILURE;
        }
        if (internetStatus == InternetStatus.UNAVAILABLE) {
            return FailureCategory.INTERNET_PATH_FAILURE;
        }
        if (signalPercent != null && signalPercent <= 40) {
            return FailureCategory.WEAK_SIGNAL;
        }
        return FailureCategory.NONE;
    }
}
