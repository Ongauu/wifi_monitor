package com.crimson.wifimonitor.service;

import com.crimson.wifimonitor.config.WifiMonitorProperties;
import com.crimson.wifimonitor.domain.enums.DnsStatus;
import com.crimson.wifimonitor.domain.enums.GatewayStatus;
import com.crimson.wifimonitor.domain.enums.InternetStatus;
import com.crimson.wifimonitor.domain.enums.WifiStatus;
import com.crimson.wifimonitor.service.model.NetworkObservation;
import com.crimson.wifimonitor.windows.CommandResult;
import com.crimson.wifimonitor.windows.IpConfigParser;
import com.crimson.wifimonitor.windows.PingResultParser;
import com.crimson.wifimonitor.windows.WlanInterfaceInfo;
import com.crimson.wifimonitor.windows.WlanInterfaceParser;
import com.crimson.wifimonitor.windows.WindowsCommandExecutor;
import java.time.Instant;
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
public class WifiStatusCollector {

    private final WindowsCommandExecutor commandExecutor;
    private final WlanInterfaceParser wlanInterfaceParser;
    private final IpConfigParser ipConfigParser;
    private final NetworkProbeService networkProbeService;
    private final WifiMonitorProperties properties;
    private final Executor wifiProbeExecutor;

    public WifiStatusCollector(
            WindowsCommandExecutor commandExecutor,
            WlanInterfaceParser wlanInterfaceParser,
            IpConfigParser ipConfigParser,
            NetworkProbeService networkProbeService,
            WifiMonitorProperties properties,
            @Qualifier("wifiProbeExecutor") Executor wifiProbeExecutor) {
        this.commandExecutor = commandExecutor;
        this.wlanInterfaceParser = wlanInterfaceParser;
        this.ipConfigParser = ipConfigParser;
        this.networkProbeService = networkProbeService;
        this.properties = properties;
        this.wifiProbeExecutor = wifiProbeExecutor;
    }

    public NetworkObservation collect() {
        Instant now = Instant.now();
        CommandResult netsh = commandExecutor.execute(List.of("netsh", "wlan", "show", "interfaces"));
        WlanInterfaceInfo wlan = wlanInterfaceParser.parse(netsh.output());
        boolean wifiConnected = wlan.connected();

        CommandResult ipconfig = commandExecutor.execute(List.of("ipconfig"));
        IpConfigParser.AdapterAddresses addresses = ipConfigParser.parse(ipconfig.output(), wlan.name());

        List<String> notes = new ArrayList<>();
        if (!netsh.succeeded()) {
            notes.add("netsh wlan show interfaces failed: "
                    + (netsh.errorMessage() != null ? netsh.errorMessage() : "exit " + netsh.exitCode()));
        }
        if (!ipconfig.succeeded()) {
            notes.add("ipconfig failed: "
                    + (ipconfig.errorMessage() != null ? ipconfig.errorMessage() : "exit " + ipconfig.exitCode()));
        }

        WifiStatus wifiStatus = mapWifiStatus(wlan, netsh);

        PingResultParser.PingStats gatewayPing = PingResultParser.PingStats.failed();
        PingResultParser.PingStats internetPing = PingResultParser.PingStats.failed();
        boolean dnsOk = false;

        if (wifiConnected) {
            CompletableFuture<PingResultParser.PingStats> gatewayFuture =
                    CompletableFuture.supplyAsync(
                                    () -> addresses.gateway() == null
                                            ? PingResultParser.PingStats.failed()
                                            : networkProbeService.pingHost(addresses.gateway()),
                                    wifiProbeExecutor)
                            .orTimeout(properties.getCommandTimeoutMs(), TimeUnit.MILLISECONDS)
                            .exceptionally(ex -> PingResultParser.PingStats.failed());
            CompletableFuture<PingResultParser.PingStats> internetFuture =
                    CompletableFuture.supplyAsync(
                                    () -> networkProbeService.pingFirstReachable(properties.getInternetHosts()),
                                    wifiProbeExecutor)
                            .orTimeout(properties.getCommandTimeoutMs() + 1000, TimeUnit.MILLISECONDS)
                            .exceptionally(ex -> PingResultParser.PingStats.failed());
            CompletableFuture<Boolean> dnsFuture = CompletableFuture.supplyAsync(
                            () -> networkProbeService.resolveDns(properties.getDnsHosts()), wifiProbeExecutor)
                    .orTimeout(properties.getCommandTimeoutMs(), TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> false);
            gatewayPing = gatewayFuture.join();
            internetPing = internetFuture.join();
            dnsOk = Boolean.TRUE.equals(dnsFuture.join());
        } else {
            notes.add("Skipped gateway/internet/DNS probes because the Wi-Fi adapter is not associated.");
        }

        GatewayStatus gatewayStatus = wifiConnected
                ? (gatewayPing.reachable() ? GatewayStatus.REACHABLE : GatewayStatus.UNREACHABLE)
                : GatewayStatus.UNKNOWN;
        InternetStatus internetStatus = wifiConnected
                ? (internetPing.reachable() ? InternetStatus.AVAILABLE : InternetStatus.UNAVAILABLE)
                : InternetStatus.UNKNOWN;
        DnsStatus dnsStatus = wifiConnected ? (dnsOk ? DnsStatus.RESOLVED : DnsStatus.FAILED) : DnsStatus.UNKNOWN;

        return NetworkObservation.builder()
                .capturedAt(now)
                .wifiStatus(wifiStatus)
                .internetStatus(internetStatus)
                .gatewayStatus(gatewayStatus)
                .dnsStatus(dnsStatus)
                .ssid(wlan.ssid())
                .interfaceName(wlan.name())
                .interfaceState(wlan.state())
                .adapterDescription(wlan.description())
                .signalPercent(wlan.signalPercent())
                .ipAddress(addresses.ipv4())
                .gatewayAddress(addresses.gateway())
                .gatewayLatencyMs(gatewayPing.averageLatencyMs())
                .internetLatencyMs(internetPing.averageLatencyMs())
                .packetLossPercent(internetPing.packetLossPercent())
                .bssid(wlan.bssid())
                .radioType(wlan.radioType())
                .authentication(wlan.authentication())
                .channel(wlan.channel())
                .receiveRateMbps(wlan.receiveRateMbps())
                .transmitRateMbps(wlan.transmitRateMbps())
                .netshOutput(wlan.rawOutput())
                .collectionNotes(String.join(" ", notes))
                .build();
    }

    private WifiStatus mapWifiStatus(WlanInterfaceInfo wlan, CommandResult netsh) {
        if (!netsh.succeeded() && (wlan.state() == null || "unknown".equalsIgnoreCase(wlan.state()))) {
            return WifiStatus.UNKNOWN;
        }
        return wlan.connected() ? WifiStatus.CONNECTED : WifiStatus.DISCONNECTED;
    }
}
