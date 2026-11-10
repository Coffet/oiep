package com.oiep.companion.read;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth boundary of the read API: the endpoint is only reachable with the exact configured
 * bearer token, and the token value is never reflected in a response body or header.
 */
class ReadApiAuthIntegrationTests extends ReadApiEnabledTestSupport {

    @Test
    void missingAuthorizationReturns401Json() throws Exception {
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.code").value("unauthorized"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void wrongMalformedAndMissingTokensAllReturn401() throws Exception {
        for (String header : new String[]{
                "Bearer wrong-token-of-sufficient-length",
                "Bearer " + TOKEN + "x",
                "Token " + TOKEN,
                "Bearer",
                "",
                "   "}) {
            mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                            .header(HttpHeaders.AUTHORIZATION, header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("unauthorized"));
        }
    }

    @Test
    void validTokenReturnsThePage() throws Exception {
        long reportId = insertPublished("Auth Co");

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH).header(HttpHeaders.AUTHORIZATION, bearerHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Robots-Tag", containsString("noindex")))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.next_cursor").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").value("oiep-r" + reportId));
    }

    @Test
    void bearerSchemeIsCaseInsensitiveAsHttpRequires() throws Exception {
        insertPublished("Case Co");

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void tokenIsNeverEchoedInAnyResponse() throws Exception {
        insertPublished("Echo Co");

        String success = bodyOf(getExperiences());
        assertThat(success).doesNotContain(TOKEN);

        String unauthorized = mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andReturn().getResponse().getContentAsString();
        assertThat(unauthorized).doesNotContain(TOKEN).doesNotContain("wrong-token");
    }
}
