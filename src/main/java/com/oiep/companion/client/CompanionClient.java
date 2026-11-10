package com.oiep.companion.client;

/**
 * Server-side client for the companion service's operator API.
 *
 * <p>Only the fixed admin routes are reachable through this interface; callers must pass a
 * path under {@code /v1/admin/}. The partner token is held by the client, never returned to
 * the browser and never logged. The interface is deliberately small so a test can replace it
 * with a stub, and so the component can be removed without touching the rest of the app.</p>
 */
public interface CompanionClient {

    /** GET an operator route, e.g. {@code /v1/admin/health}. */
    CompanionResponse get(String pathAndQuery);

    /** POST a JSON body to an operator route, e.g. {@code /v1/admin/chat}. */
    CompanionResponse postJson(String path, String jsonBody);
}
