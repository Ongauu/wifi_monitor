package com.crimson.wifimonitor.domain.entity;

import com.crimson.wifimonitor.domain.enums.FailureCategory;
import com.crimson.wifimonitor.domain.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
        name = "wifi_connection_sessions",
        indexes = {
            @Index(name = "idx_sessions_started_at", columnList = "startedAt"),
            @Index(name = "idx_sessions_status", columnList = "status"),
            @Index(name = "idx_sessions_ssid", columnList = "ssid")
        })
public class WifiConnectionSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String ssid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    private Long durationMs;

    @Column(length = 64)
    private String startIpAddress;

    @Column(length = 64)
    private String endIpAddress;

    private Integer startSignalPercent;

    private Integer endSignalPercent;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private FailureCategory disconnectReason;

    @Column(nullable = false)
    private Integer eventCount = 0;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (eventCount == null) {
            eventCount = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
