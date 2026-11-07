package com.crimson.wifimonitor.windows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PingResultParserTest {

    private final PingResultParser parser = new PingResultParser();

    @Test
    void parsesSuccessfulPing() {
        String output = """
                Pinging 8.8.8.8 with 32 bytes of data:
                Reply from 8.8.8.8: bytes=32 time=12ms TTL=118

                Ping statistics for 8.8.8.8:
                    Packets: Sent = 2, Received = 2, Lost = 0 (0% loss),
                Approximate round trip times in milli-seconds:
                    Minimum = 11ms, Maximum = 12ms, Average = 12ms
                """;

        PingResultParser.PingStats stats = parser.parse(output);
        assertThat(stats.reachable()).isTrue();
        assertThat(stats.averageLatencyMs()).isEqualTo(12.0);
        assertThat(stats.packetLossPercent()).isEqualTo(0.0);
    }

    @Test
    void parsesPacketLoss() {
        String output = """
                    Packets: Sent = 4, Received = 0, Lost = 4 (100% loss),
                """;
        PingResultParser.PingStats stats = parser.parse(output);
        assertThat(stats.reachable()).isFalse();
        assertThat(stats.packetLossPercent()).isEqualTo(100.0);
    }
}
