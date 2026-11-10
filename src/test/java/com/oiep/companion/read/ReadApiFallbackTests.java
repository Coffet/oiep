package com.oiep.companion.read;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * While the read API is enabled, the component-owned {@code /api/v1/**} namespace answers with
 * the JSON error envelope instead of the application's HTML error page. This only applies to
 * the component's own prefix; no core route is claimed.
 */
class ReadApiFallbackTests extends ReadApiEnabledTestSupport {

    @Test
    void unknownApiPathReturnsJsonNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist")
                        .header("Authorization", bearerHeader()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Robots-Tag", containsString("noindex")))
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void wrongHttpMethodOnTheReadNamespaceReturnsJsonNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/experiences")
                        .header("Authorization", bearerHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void unknownPathWithoutCredentialsAlsoGetsTheJsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }
}
