package com.crimson.wifimonitor.repository;

import com.crimson.wifimonitor.domain.entity.WifiConnectionSession;
import com.crimson.wifimonitor.domain.enums.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WifiConnectionSessionRepository extends JpaRepository<WifiConnectionSession, Long> {

    Optional<WifiConnectionSession> findFirstByStatusOrderByStartedAtDesc(SessionStatus status);

    Page<WifiConnectionSession> findByStartedAtBetween(Instant from, Instant to, Pageable pageable);

    List<WifiConnectionSession> findByStartedAtGreaterThanEqualOrderByStartedAtAsc(Instant from);

    @Query("""
            select coalesce(sum(s.durationMs), 0)
            from WifiConnectionSession s
            where s.status = com.crimson.wifimonitor.domain.enums.SessionStatus.CLOSED
              and s.startedAt between :from and :to
            """)
    Long sumClosedDurationBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select avg(s.durationMs)
            from WifiConnectionSession s
            where s.status = com.crimson.wifimonitor.domain.enums.SessionStatus.CLOSED
              and s.durationMs is not null
              and s.startedAt between :from and :to
            """)
    Double averageClosedDurationBetween(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatusAndStartedAtBetween(SessionStatus status, Instant from, Instant to);
}
