package com.oiep.companion.read;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Response headers that every read-API response carries, including the error envelopes.
 *
 * <p>The endpoint serves moderation-hidden report data to a service token and never any
 * browser: responses are therefore never cacheable and must never be indexed. CORS headers are
 * deliberately absent, so no browser origin receives a readable cross-origin response.</p>
 *
 * <p>Headers are written directly to the servlet response with {@code setHeader} (which
 * replaces any earlier value, e.g. the baseline {@code no-cache} from the security-headers
 * filter). The read API's stricter {@code no-store} must be the single effective value seen by
 * a proxy.</p>
 */
final class ReadApiHeaders {

    static final String CACHE_CONTROL_VALUE = "no-store";
    static final String PRAGMA_VALUE = "no-cache";
    static final String X_ROBOTS_TAG_VALUE = "noindex, nofollow";

    private ReadApiHeaders() {
    }

    static void apply(HttpServletResponse response) {
        response.setHeader("Cache-Control", CACHE_CONTROL_VALUE);
        response.setHeader("Pragma", PRAGMA_VALUE);
        response.setHeader("X-Robots-Tag", X_ROBOTS_TAG_VALUE);
    }
}
