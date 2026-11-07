package com.crimson.wifimonitor.domain.entity;

import com.crimson.wifimonitor.domain.enums.EventType;
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
        name = "wifi_diagnostic_snapshots",
        indexes = {
            @Index(name = "idx_snapshots_captured_at", columnList = "capturedAt"),
            @Index(name = "idx_snapshots_event_id", columnList = "event_id")
        })
public class WifiDiagnosticSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private WifiConnectionEvent event;

    @Column(nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private EventType triggerEventType;

    @Column(length = 64)
    private String adapterState;

    @Column(length = 64)
    private String radioType;

    @Column(length = 64)
    private String authentication;

    @Column(length = 64)
    private String bssid;

    @Column(length = 32)
    private String channel;

    @Column(length = 32)
    private String receiveRateMbps;

    @Column(length = 32)
    private String transmitRateMbps;

    @Column(columnDefinition = "TEXT")
    private String netshInterfacesOutput;

    @Column(columnDefinition = "TEXT")
    private String wlanAutoconfigEvents;

    @Column(length = 1024)
    private String wlanReportPath;

    @Column(columnDefinition = "TEXT")
    private String inferredCauses;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
