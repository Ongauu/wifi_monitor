package com.crimson.wifimonitor.service;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import com.crimson.wifimonitor.windows.CommandResult;
import com.crimson.wifimonitor.windows.PingResultParser;
import com.crimson.wifimonitor.windows.WindowsCommandExecutor;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NetworkProbeService {

    private final WindowsCommandExecutor commandExecutor;
    private final PingResultParser pingResultParser;
    private final WifiMonitorProperties properties;
    private final Executor wifiProbeExecutor;

    public NetworkProbeService(
            WindowsCommandExecutor commandExecutor,
            PingResultParser pingResultParser,
            WifiMonitorProperties properties,
            @Qualifier("wifiProbeExecutor") Executor wifiProbeExecutor) {
        this.commandExecutor = commandExecutor;
        this.pingResultParser = pingResultParser;
        this.properties = properties;
        this.wifiProbeExecutor = wifiProbeExecutor;
    }

    public PingResultParser.PingStats pingHost(String host) {
        List<String> command = List.of(
                "ping",
                "-n",
                String.valueOf(properties.getPingCount()),
                "-w",
                String.valueOf(properties.getPingTimeoutMs()),
                host);
        long timeout = properties.getPingTimeoutMs() * properties.getPingCount() + 1500;
        CommandResult result = commandExecutor.execute(command, Duration.ofMillis(timeout));
        if (result.timedOut() || result.output().isBlank()) {
            return PingResultParser.PingStats.failed();
        }
        return pingResultParser.parse(result.output());
    }

    public PingResultParser.PingStats pingFirstReachable(List<String> hosts) {
        PingResultParser.PingStats last = PingResultParser.PingStats.failed();
        for (String host : hosts) {
            PingResultParser.PingStats stats = pingHost(host);
            if (stats.reachable()) {
                return stats;
            }
            last = stats;
        }
        return last;
    }

    public boolean resolveDns(List<String> hosts) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String host : hosts) {
            futures.add(CompletableFuture.supplyAsync(() -> resolveOne(host), wifiProbeExecutor)
                    .orTimeout(properties.getCommandTimeoutMs(), TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> false));
        }
        for (CompletableFuture<Boolean> future : futures) {
            try {
                if (Boolean.TRUE.equals(future.join())) {
                    return true;
                }
            } catch (Exception ex) {
                log.debug("DNS future failed: {}", ex.getMessage());
            }
        }
        return false;
    }

    private boolean resolveOne(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            return addresses != null && addresses.length > 0;
        } catch (Exception ex) {
            log.debug("DNS lookup failed for {}: {}", host, ex.getMessage());
            return false;
        }
    }
}
