package com.crimson.wifimonitor.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WifiStatisticsResponse {
    Instant from;
    Instant to;
    long totalWifiDisconnections;
    long totalInternetLossEvents;
    long totalDnsFailures;
    long totalGatewayFailures;
    Double totalConnectionTimeMinutes;
    Double totalDowntimeMinutes;
    Double averageConnectionDurationMinutes;
    Double averageDisconnectionDurationMinutes;
    Double longestDisconnectionMinutes;
    Double averageSignalStrengthPercent;
    Double internetAvailabilityPercent;
}
