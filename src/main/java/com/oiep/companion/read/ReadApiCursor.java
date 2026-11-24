package com.oiep.companion.read;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque pagination cursor for the read-only experiences API.
 *
 * <p>The cursor is the unpadded base64url encoding of {@code "<eventEpochMillis>:<reportId>"}
 * and simply names the last {@code (event_ms, report_id)} position a page returned. It contains
 * no secret (both values are internal, monotonic keys) and is only meaningful against the same
 * database.</p>
 *
 * @param eventMs  the component event timestamp the last item was ordered by
 * @param reportId the report id that broke the timestamp tie
 */
public record ReadApiCursor(long eventMs, long reportId) {

    /**
     * Longest encoded cursor accepted, in characters.
     *
     * <p>The endpoint emits the unpadded base64url form of
     * {@code "<eventEpochMillis>:<reportId>"}: typically 13 + 1 + 19 = 33 payload bytes, i.e. 44
     * characters at most (even two 19-digit longs stay at 39 bytes / 52 characters). 64 leaves
     * headroom while keeping the value far below the input sizes that HotSpot's Base64 decode
     * intrinsic processes in long blocks. That bound is deliberate hardening for CVE-2026-114938
     * ("Java4Shell"), whose JDK 17 trigger is 128 bytes of padded input: capping the value before
     * it reaches the decoder means this endpoint cannot deliver the trigger even on an unpatched
     * JVM.</p>
     */
    private static final int MAX_ENCODED_LENGTH = 64;
    /** Longest decoded cursor accepted, in bytes (the payload above is at most 39). */
    private static final int MAX_DECODED_LENGTH = 64;
    private static final char SEPARATOR = ':';

    /** The opaque wire form (unpadded base64url, safe in a query string without re-encoding). */
    public String encode() {
        String plain = eventMs + ":" + reportId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a wire cursor. Only the exact wire form is accepted: unpadded base64url
     * ({@code [A-Za-z0-9_-]+}) of the documented payload. Padded base64 ({@code =}) is rejected
     * rather than tolerated: {@link #encode()} never emits it, it is not needed by the only
     * consumer (the companion echoes the cursor verbatim), and a short padded tail is exactly
     * what CVE-2026-114938's decoder bug mishandles. Any value that is not the expected shape is
     * rejected with {@code 400 invalid_cursor}; the rejected value is never echoed or logged.
     */
    public static ReadApiCursor decode(String raw) {
        String encoded = ReadApiTimestamps.trimToEmpty(raw);
        if (!isWireForm(encoded)) {
            throw ReadApiRequestException.badRequest("invalid_cursor");
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw ReadApiRequestException.badRequest("invalid_cursor");
        }
        if (decoded.length == 0 || decoded.length > MAX_DECODED_LENGTH) {
            throw ReadApiRequestException.badRequest("invalid_cursor");
        }
        String plain = new String(decoded, StandardCharsets.UTF_8);
        int separator = plain.indexOf(SEPARATOR);
        if (separator <= 0 || separator != plain.lastIndexOf(SEPARATOR)
                || separator == plain.length() - 1) {
            throw ReadApiRequestException.badRequest("invalid_cursor");
        }
        long eventMs;
        long reportId;
        try {
            eventMs = Long.parseLong(plain.substring(0, separator));
            reportId = Long.parseLong(plain.substring(separator + 1));
        } catch (NumberFormatException ex) {
            throw ReadApiRequestException.badRequest("invalid_cursor");
        }
        if (eventMs < 0L || reportId <= 0L) {
            throw ReadApiRequestException.badRequest("invalid_cursor");
        }
        return new ReadApiCursor(eventMs, reportId);
    }

    /**
     * True for a bounded, unpadded URL-safe base64 value.
     *
     * <p>This runs before the decoder so that no other character set (padding, standard-base64
     * {@code +}/{@code /}, whitespace, anything Unicode) reaches it, and so oversized values are
     * rejected without allocating a decode buffer.</p>
     */
    private static boolean isWireForm(String encoded) {
        if (encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH) {
            return false;
        }
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            boolean urlSafe = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!urlSafe) {
                return false;
            }
        }
        return true;
    }
}
