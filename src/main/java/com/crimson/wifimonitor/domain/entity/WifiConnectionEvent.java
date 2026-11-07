package com.crimson.wifimonitor.domain.entity;

import com.crimson.wifimonitor.domain.enums.DnsStatus;
import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.domain.enums.FailureCategory;
import com.crimson.wifimonitor.domain.enums.GatewayStatus;
import com.crimson.wifimonitor.domain.enums.InternetStatus;
import com.crimson.wifimonitor.domain.enums.WifiStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "wifi_connection_events",
        indexes = {
            @Index(name = "idx_events_occurred_at", columnList = "occurredAt"),
            @Index(name = "idx_events_type", columnList = "eventType"),
            @Index(name = "idx_events_ssid", columnList = "ssid"),
            @Index(name = "idx_events_session_id", columnList = "session_id"),
            @Index(name = "idx_events_failure_category", columnList = "failureCategory")
        })
public class WifiConnectionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private WifiConnectionSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private FailureCategory failureCategory;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(length = 255)
    private String ssid;

    private Integer signalStrengthPercent;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 128)
    private String interfaceName;

    @Column(length = 64)
    private String interfaceState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WifiStatus wifiStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GatewayStatus gatewayStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InternetStatus internetStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DnsStatus dnsStatus;

    @Column(length = 64)
    private String gatewayAddress;

    private Double gatewayLatencyMs;

    private Double internetLatencyMs;

    private Double packetLossPercent;

    private Long connectionUptimeMs;

    private Long disconnectionDurationMs;

    @Column(columnDefinition = "TEXT")
    private String diagnosticSummary;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
