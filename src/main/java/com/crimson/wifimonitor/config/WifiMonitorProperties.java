package com.crimson.wifimonitor.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "wifi.monitor")
public class WifiMonitorProperties {

    private boolean enabled = true;

    @Min(1000)
    private long intervalMs = 5000;

    @Min(500)
    private long commandTimeoutMs = 4000;

    @Min(1)
    private int pingCount = 2;

    @Min(200)
    private long pingTimeoutMs = 1000;

    @NotEmpty
    private List<String> internetHosts = List.of("8.8.8.8", "1.1.1.1");

    @NotEmpty
    private List<String> dnsHosts = List.of("www.google.com", "www.cloudflare.com");

    @Min(1)
    private int signalLowThresholdPercent = 40;

    @Min(1)
    private int signalRecoverThresholdPercent = 50;

    private boolean collectWlanEventsOnDisconnect = true;

    private boolean collectWlanReportOnDisconnect = false;

    @Min(1)
    private int wlanReportMinIntervalMinutes = 30;

    @Min(1000)
    private int maxCommandOutputChars = 20_000;

    @Min(1)
    private int analysisLookbackHours = 72;
}
