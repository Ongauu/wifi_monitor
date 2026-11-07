package com.crimson.wifimonitor.scheduler;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import com.crimson.wifimonitor.service.WifiMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WifiMonitorScheduler {

    private final WifiMonitorService wifiMonitorService;
    private final WifiMonitorProperties properties;

    @Scheduled(fixedDelayString = "${wifi.monitor.interval-ms:5000}", initialDelayString = "1500")
    public void monitor() {
        if (!properties.isEnabled()) {
            return;
        }
        wifiMonitorService.poll();
    }
}
