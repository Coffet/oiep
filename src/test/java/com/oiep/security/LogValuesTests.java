package com.oiep.security;

import com.oiep.web.LogValues;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Request-controlled values must never be logged raw (log forging / oversized lines). */
class LogValuesTests {

    @Test
    void controlCharactersAreNeutralised() {
        assertThat(LogValues.safe("admin\r\nINFO forged line")).isEqualTo("admin??INFO forged line");
        assertThat(LogValues.safe("tab\tand\u0000null")).isEqualTo("tab?and?null");
    }

    @Test
    void longValuesAreTruncated() {
        String truncated = LogValues.safe("x".repeat(500));
        assertThat(truncated.length()).isEqualTo(121);
        assertThat(truncated).endsWith("…");
        assertThat(LogValues.safe("short")).isEqualTo("short");
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(LogValues.safe(null)).isEmpty();
        assertThat(LogValues.safe("")).isEmpty();
        assertThat(LogValues.safe("abc", 0)).isEqualTo("a…");
    }
}
