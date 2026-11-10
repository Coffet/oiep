package com.oiep.companion.read;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Lenient ISO-8601 helpers for the read API.
 *
 * <p>Outgoing timestamps are always normalised to {@link Instant#toString()} (UTC, {@code Z}).
 * Incoming {@code updated_since} values are parsed from the common ISO-8601 spellings an
 * operator or the companion service may send: an instant with {@code Z}, an offset date-time,
 * or a zone-less local date-time (interpreted as UTC, the platform's storage time zone).</p>
 */
final class ReadApiTimestamps {

    private ReadApiTimestamps() {
    }

    /** Millisecond epoch for an ISO-8601 value, or {@code null} when it cannot be parsed. */
    static Long epochMillisOrNull(String raw) {
        String candidate = trimToEmpty(raw);
        if (candidate.isEmpty()) {
            return null;
        }
        Instant instant = parseInstant(candidate);
        return instant == null ? null : instant.toEpochMilli();
    }

    /** ISO-8601 UTC rendering of an epoch-millisecond timestamp. */
    static String isoFromEpochMillis(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    /**
     * ISO-8601 UTC rendering of a stored value, falling back to {@code fallback} when the
     * stored value is blank or unparsable (a hand-edited database is an external boundary).
     */
    static String isoOr(String raw, String fallback) {
        String candidate = trimToEmpty(raw);
        if (candidate.isEmpty()) {
            return fallback;
        }
        Instant instant = parseInstant(candidate);
        return instant == null ? fallback : instant.toString();
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through to the offset / local-date-time spellings
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
