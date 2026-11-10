package com.oiep.companion.read;

/**
 * JSON error envelope for the read-only experiences API.
 *
 * <p>Shape: {@code {"error":{"code":"unauthorized"}}}. Codes are fixed, lower-snake-case
 * identifiers; no request value (token, cursor, query string, path) is ever reflected into the
 * envelope or the log.</p>
 *
 * @param error the error detail
 */
public record ReadApiError(ErrorDetail error) {

    /** The fixed error code wrapper. */
    public record ErrorDetail(String code) {
    }

    public static ReadApiError of(String code) {
        return new ReadApiError(new ErrorDetail(code));
    }
}
