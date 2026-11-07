package com.crimson.wifimonitor.dto;

import com.crimson.wifimonitor.domain.enums.FailureCategory;
import com.crimson.wifimonitor.domain.enums.SessionStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WifiSessionResponse {
    Long sessionId;
    String ssid;
    SessionStatus status;
    Instant startedAt;
    Instant endedAt;
    Double durationMinutes;
    String startIpAddress;
    String endIpAddress;
    Integer startSignalPercent;
    Integer endSignalPercent;
    FailureCategory disconnectReason;
    Integer eventCount;
}
