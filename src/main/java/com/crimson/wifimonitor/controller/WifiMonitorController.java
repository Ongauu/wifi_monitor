package com.crimson.wifimonitor.controller;

import com.crimson.wifimonitor.domain.enums.EventType;
import com.crimson.wifimonitor.dto.RootCauseFindingResponse;
import com.crimson.wifimonitor.dto.WifiDiagnosticResponse;
import com.crimson.wifimonitor.dto.WifiEventResponse;
import com.crimson.wifimonitor.dto.WifiSessionResponse;
import com.crimson.wifimonitor.dto.WifiStatisticsResponse;
import com.crimson.wifimonitor.dto.WifiStatusResponse;
import com.crimson.wifimonitor.service.RootCauseAnalysisService;
import com.crimson.wifimonitor.service.WifiQueryService;
import com.crimson.wifimonitor.service.WifiStatisticsService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wifi")
@RequiredArgsConstructor
public class WifiMonitorController {

    private final WifiQueryService queryService;
    private final WifiStatisticsService statisticsService;
    private final RootCauseAnalysisService analysisService;

    @GetMapping("/status")
    public WifiStatusResponse status() {
        return queryService.currentStatus();
    }

    @GetMapping("/events")
    public Page<WifiEventResponse> events(
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Range range = range(from, to);
        return queryService.events(
                type, range.from, range.to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }

    @GetMapping("/sessions")
    public Page<WifiSessionResponse> sessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Range range = range(from, to);
        return queryService.sessions(
                range.from, range.to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")));
    }

    @GetMapping("/statistics")
    public WifiStatisticsResponse statistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Range range = range(from, to);
        return statisticsService.statistics(range.from, range.to);
    }

    @GetMapping("/diagnostics")
    public Page<WifiDiagnosticResponse> diagnostics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Range range = range(from, to);
        return queryService.diagnostics(
                range.from, range.to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "capturedAt")));
    }

    @GetMapping("/analysis")
    public List<RootCauseFindingResponse> analysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        if (from == null && to == null) {
            return analysisService.analyzeDefaultWindow();
        }
        Range range = range(from, to);
        return analysisService.analyze(range.from, range.to);
    }

    private Range range(Instant from, Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(7, ChronoUnit.DAYS) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        return new Range(start, end);
    }

    private record Range(Instant from, Instant to) {}
}
