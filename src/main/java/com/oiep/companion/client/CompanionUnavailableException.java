package com.oiep.companion.client;

/**
 * The companion service could not be reached or answered with an unusable response.
 *
 * <p>The message is safe to log and to show to the operator: it never contains the bearer
 * token, the request body or the full request URL.</p>
 */
public class CompanionUnavailableException extends RuntimeException {

    public CompanionUnavailableException(String message) {
        super(message);
    }

    public CompanionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
