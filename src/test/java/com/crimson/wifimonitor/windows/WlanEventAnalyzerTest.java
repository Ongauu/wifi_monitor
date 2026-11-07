package com.crimson.wifimonitor.windows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WlanEventAnalyzerTest {

    private final WlanEventAnalyzer analyzer = new WlanEventAnalyzer();

    @Test
    void detectsAuthenticationAndDriverHints() {
        String log = """
                The wireless network authentication failed.
                The adapter driver reported a reset.
                """;
        assertThat(analyzer.inferCauses(log))
                .anyMatch(s -> s.toLowerCase().contains("authentication"))
                .anyMatch(s -> s.toLowerCase().contains("driver"));
    }
}
