package com.oiep.companion.read;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timestamp parsing/normalisation for the {@code updated_since} watermark and outgoing
 * ISO-8601 values. Both the companion service (which sends whole-second {@code Z} values) and an
 * operator's curl (which may send an offset) must be accepted.
 */
class ReadApiTimestampsTests {

    private static final Instant SAMPLE = Instant.parse("2026-11-10T13:04:05.123Z");

    @Test
    void parsesEveryAcceptedIsoSpelling() {
        assertThat(ReadApiTimestamps.epochMillisOrNull("2026-11-10T13:04:05Z"))
                .isEqualTo(Instant.parse("2026-11-10T13:04:05Z").toEpochMilli());
        assertThat(ReadApiTimestamps.epochMillisOrNull("2026-11-10T13:04:05.123Z"))
                .isEqualTo(SAMPLE.toEpochMilli());
        assertThat(ReadApiTimestamps.epochMillisOrNull("2026-11-10T21:04:05+08:00"))
                .isEqualTo(Instant.parse("2026-11-10T13:04:05Z").toEpochMilli());
        assertThat(ReadApiTimestamps.epochMillisOrNull("2026-11-10T13:04:05"))
                .isEqualTo(Instant.parse("2026-11-10T13:04:05Z").toEpochMilli());
        assertThat(ReadApiTimestamps.epochMillisOrNull("  2026-11-10T13:04:05Z  "))
                .isEqualTo(Instant.parse("2026-11-10T13:04:05Z").toEpochMilli());
    }

    @Test
    void rejectsUnparsableOrBlankValues() {
        assertThat(ReadApiTimestamps.epochMillisOrNull(null)).isNull();
        assertThat(ReadApiTimestamps.epochMillisOrNull("")).isNull();
        assertThat(ReadApiTimestamps.epochMillisOrNull("not-a-date")).isNull();
        assertThat(ReadApiTimestamps.epochMillisOrNull("2026-13-40T99:99:99Z")).isNull();
    }

    @Test
    void formatsEpochMillisAsIsoZAndFallsBackForUnparsableStoredValues() {
        assertThat(ReadApiTimestamps.isoFromEpochMillis(SAMPLE.toEpochMilli()))
                .isEqualTo("2026-11-10T13:04:05.123Z");
        assertThat(ReadApiTimestamps.isoOr("2026-11-10T13:04:05Z", "fallback"))
                .isEqualTo("2026-11-10T13:04:05Z");
        assertThat(ReadApiTimestamps.isoOr("2026-11-10T21:04:05+08:00", "fallback"))
                .isEqualTo("2026-11-10T13:04:05Z");
        assertThat(ReadApiTimestamps.isoOr("garbage", "fallback")).isEqualTo("fallback");
        assertThat(ReadApiTimestamps.isoOr("", "fallback")).isEqualTo("fallback");
        assertThat(ReadApiTimestamps.isoOr(null, "fallback")).isEqualTo("fallback");
    }
}
