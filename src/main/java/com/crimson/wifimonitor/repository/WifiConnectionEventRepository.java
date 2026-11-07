package com.crimson.wifimonitor.repository;

import com.crimson.wifimonitor.domain.entity.WifiConnectionEvent;
import com.crimson.wifimonitor.domain.enums.EventType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WifiConnectionEventRepository extends JpaRepository<WifiConnectionEvent, Long> {

    Optional<WifiConnectionEvent> findTopByOrderByOccurredAtDesc();

    Page<WifiConnectionEvent> findByOccurredAtBetween(Instant from, Instant to, Pageable pageable);

    Page<WifiConnectionEvent> findByEventTypeAndOccurredAtBetween(
            EventType eventType, Instant from, Instant to, Pageable pageable);

    List<WifiConnectionEvent> findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(Instant from);

    long countByEventTypeAndOccurredAtBetween(EventType eventType, Instant from, Instant to);

    @Query("""
            select avg(e.signalStrengthPercent)
            from WifiConnectionEvent e
            where e.signalStrengthPercent is not null
              and e.occurredAt between :from and :to
            """)
    Double averageSignalBetween(@Param("from") Instant from, @Param("to") Instant to);
}
