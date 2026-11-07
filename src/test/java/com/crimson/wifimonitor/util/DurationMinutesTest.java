package com.crimson.wifimonitor.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DurationMinutesTest {

    @Test
    void convertsMillisecondsToMinutes() {
        assertThat(DurationMinutes.fromMillis((Long) null)).isNull();
        assertThat(DurationMinutes.fromMillis(0L)).isEqualTo(0.0);
        assertThat(DurationMinutes.fromMillis(30_000L)).isEqualTo(0.5);
        assertThat(DurationMinutes.fromMillis(120_000L)).isEqualTo(2.0);
        assertThat(DurationMinutes.fromMillis(5_000L)).isEqualTo(0.08);
    }
}
