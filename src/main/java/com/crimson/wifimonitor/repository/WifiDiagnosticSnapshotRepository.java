package com.crimson.wifimonitor.repository;

import com.crimson.wifimonitor.domain.entity.WifiDiagnosticSnapshot;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WifiDiagnosticSnapshotRepository extends JpaRepository<WifiDiagnosticSnapshot, Long> {

    Page<WifiDiagnosticSnapshot> findByCapturedAtBetween(Instant from, Instant to, Pageable pageable);

    Optional<WifiDiagnosticSnapshot> findTopByWlanReportPathIsNotNullOrderByCapturedAtDesc();
}
