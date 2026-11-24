package com.oiep.companion.read;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cursor round-trip and malformed-cursor handling. The cursor is opaque and contains no secret,
 * but it must be strictly validated so garbage input produces a JSON 400 rather than a 500 or a
 * request that silently restarts from the beginning.
 */
class ReadApiCursorTests {

    @Test
    void encodesAsUnpaddedBase64UrlAndRoundTrips() {
        ReadApiCursor cursor = new ReadApiCursor(1_762_765_445_123L, 42L);

        String encoded = cursor.encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(encoded).doesNotContain("=");
        assertThat(ReadApiCursor.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsPaddedFormBecauseTheWireFormIsUnpadded() {
        // A payload whose length is not a multiple of 3, so canonical padding is non-empty.
        String encoded = new ReadApiCursor(1_762_765_445_123L, 42L).encode();
        String padded = encoded + "=".repeat((4 - encoded.length() % 4) % 4);

        assertThat(padded).isNotEqualTo(encoded);
        assertInvalid(padded);
        // Padding is rejected even when the value is short enough for the length guard.
        assertInvalid("A".repeat(63) + "=");
    }

    @Test
    void rejectsCursorsAtOrAboveTheJava4ShellTriggerSize() {
        // CVE-2026-114938 ("Java4Shell"): a padded Base64 value with a short tail can drive
        // HotSpot's AVX-512 Base64 decode intrinsic into an out-of-bounds heap write. On JDK 17
        // the trigger is 128 bytes before the trailing '='. The decoder must never see the
        // value, whatever the JVM build: the length/charset guard rejects it first.
        assertInvalid("A".repeat(128) + "=");
        assertInvalid("A".repeat(64) + "=");
        assertInvalid("A".repeat(128) + "==");
    }

    @Test
    void acceptsTheLongestLegitimateCursor() {
        ReadApiCursor cursor = new ReadApiCursor(Long.MAX_VALUE, Long.MAX_VALUE);

        String encoded = cursor.encode();

        assertThat(encoded.length()).isLessThanOrEqualTo(64);
        assertThat(ReadApiCursor.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsMissingEmptyAndOversizedCursors() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("   ");
        assertInvalid("a".repeat(65));
        assertInvalid("a".repeat(300));
    }

    @Test
    void rejectsNonBase64AndNonNumericPayloads() {
        assertInvalid("@@not-base64@@");
        assertInvalid("ab+cd");
        assertInvalid("ab/cd");
        assertInvalid(base64("abc"));
        assertInvalid(base64("1:2:3"));
        assertInvalid(base64(":2"));
        assertInvalid(base64("1:"));
        assertInvalid(base64("abc:def"));
        assertInvalid(base64("-5:10"));
        assertInvalid(base64("10:-5"));
        assertInvalid(base64("10:0"));
        assertInvalid(base64("0:0"));
    }

    @Test
    void rejectsPayloadThatIsNotUtf8Literal() {
        assertInvalid(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[]{(byte) 0xFF, (byte) 0xFE, ':', '1'}));
    }

    @Test
    void acceptedCursorCanBeDecodedBackIntoIsolatedValues() {
        ReadApiCursor cursor = ReadApiCursor.decode(base64("1762765445123:99"));

        assertThat(cursor.eventMs()).isEqualTo(1_762_765_445_123L);
        assertThat(cursor.reportId()).isEqualTo(99L);
    }

    private static void assertInvalid(String rawCursor) {
        assertThatThrownBy(() -> ReadApiCursor.decode(rawCursor))
                .isInstanceOf(ReadApiRequestException.class)
                .satisfies(exception -> assertThat(((ReadApiRequestException) exception).code())
                        .isEqualTo("invalid_cursor"));
    }

    private static String base64(String plain) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }
}
