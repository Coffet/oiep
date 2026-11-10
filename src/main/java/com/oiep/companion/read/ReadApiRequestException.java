package com.oiep.companion.read;

import org.springframework.http.HttpStatus;

/**
 * A safe, caller-facing read API failure: the HTTP status plus a fixed error code.
 *
 * <p>The message is deliberately the code itself so that no request value (token, cursor,
 * {@code updated_since} or page-size input) can ever leak into an exception message, the JSON
 * error envelope or a log line.</p>
 */
public final class ReadApiRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;
    private final int retryAfterSeconds;

    private ReadApiRequestException(HttpStatus status, String code) {
        this(status, code, 0);
    }

    private ReadApiRequestException(HttpStatus status, String code, int retryAfterSeconds) {
        super(code, null, false, false);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    /** Missing or invalid bearer credentials: 401 {@code unauthorized}. */
    public static ReadApiRequestException unauthorized() {
        return new ReadApiRequestException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    /** A malformed query parameter: 400 with the supplied fixed code. */
    public static ReadApiRequestException badRequest(String code) {
        return new ReadApiRequestException(HttpStatus.BAD_REQUEST, code);
    }

    /** Per-client throttle exhausted: 429 {@code rate_limited} with a Retry-After value. */
    public static ReadApiRequestException rateLimited(int retryAfterSeconds) {
        return new ReadApiRequestException(
                HttpStatus.TOO_MANY_REQUESTS, "rate_limited", retryAfterSeconds);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
