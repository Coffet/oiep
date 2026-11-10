package com.oiep.companion.read;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public read API is throttled per client, including failed token attempts. This is the
 * brute-force/abuse boundary that a bearer-only endpoint on the public internet needs in
 * addition to constant-time comparison.
 */
@TestPropertySource(properties = {
        "oiep.companion.read-api.rate-limit.max-requests=5",
        "oiep.companion.read-api.rate-limit.window-seconds=60"
})
class ReadApiRateLimitTests extends ReadApiEnabledTestSupport {

    @Test
    void failedTokenAttemptsAreThrottledWithRetryAfter() throws Exception {
        // The first five attempts are processed (and rejected with 401); the sixth is
        // throttled before the token comparison.
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("unauthorized"));
        }

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.error.code").value("rate_limited"))
                .andExpect(content().string(not(containsString("wrong-token"))));
    }

    @Test
    void throttleAppliesToSuccessfulRequestsToo() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            getExperiences();
        }
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerHeader()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("rate_limited"));
    }
}
