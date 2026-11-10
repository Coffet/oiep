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

    private static final int MAX_ENCODED_LENGTH = 256;
    private static final int MAX_DECODED_LENGTH = 128;
    private static final char SEPARATOR = ':';

    /** The opaque wire form (unpadded base64url, safe in a query string without re-encoding). */
    public String encode() {
        String plain = eventMs + ":" + reportId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a wire cursor. Any value that is not the exact expected shape is rejected with
     * {@code 400 invalid_cursor}; the rejected value is never echoed or logged.
     */
    public static ReadApiCursor decode(String raw) {
        String encoded = ReadApiTimestamps.trimToEmpty(raw);
        if (encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH) {
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
}
