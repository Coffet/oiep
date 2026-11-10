package com.oiep.companion.client;

/**
 * One raw response from the companion service.
 *
 * @param status HTTP status code
 * @param body   response body (JSON for the admin API)
 */
public record CompanionResponse(int status, String body) {

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
