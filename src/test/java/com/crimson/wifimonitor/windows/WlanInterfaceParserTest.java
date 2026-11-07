package com.crimson.wifimonitor.windows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WlanInterfaceParserTest {

    private final WlanInterfaceParser parser = new WlanInterfaceParser();

    @Test
    void parsesConnectedInterface() {
        String output = """
                There is 1 interface on the system:

                    Name                   : Wi-Fi
                    Description            : Intel(R) Wi-Fi 6 AX201 160MHz
                    State                  : connected
                    SSID                   : HomeNet
                    BSSID                  : 11:22:33:44:55:66
                    Radio type             : 802.11ax
                    Authentication         : WPA2-Personal
                    Channel                : 36
                    Receive rate (Mbps)    : 1201
                    Transmit rate (Mbps)   : 1201
                    Signal                 : 88%
                    Profile                : HomeNet
                """;

        WlanInterfaceInfo info = parser.parse(output);

        assertThat(info.connected()).isTrue();
        assertThat(info.name()).isEqualTo("Wi-Fi");
        assertThat(info.ssid()).isEqualTo("HomeNet");
        assertThat(info.signalPercent()).isEqualTo(88);
        assertThat(info.channel()).isEqualTo("36");
        assertThat(info.authentication()).isEqualTo("WPA2-Personal");
    }

    @Test
    void parsesDisconnectedInterface() {
        String output = """
                    Name                   : Wi-Fi
                    State                  : disconnected
                    Signal                 :
                """;

        WlanInterfaceInfo info = parser.parse(output);

        assertThat(info.connected()).isFalse();
        assertThat(info.state()).isEqualTo("disconnected");
        assertThat(info.ssid()).isNull();
    }
}
