package com.oiep.security;

import com.oiep.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression cover for the anti-DoS controls around anonymous session creation and the
 * per-client throttles that back them up.
 */
class SessionAndThrottleHardeningTests extends IntegrationTestSupport {

    private static final String ADMIN_USER = "test-admin";

    @Test
    void loginPageRequestsAreThrottledPerClient() throws Exception {
        for (int request = 0; request < 30; request++) {
            mockMvc.perform(get("/admin/login"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void csrfFailureOnLoginDoesNotAllocateASession() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/login")
                        .param("_csrf", "not-the-token")
                        .param("username", ADMIN_USER)
                        .param("password", "whatever"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .as("a CSRF failure must not create a JSESSIONID")
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
    }

    @Test
    void helpfulVotesAreThrottledPerClient() throws Exception {
        long reportId = insertSampleReport("Vote Target Co", daysAgo(1));

        for (int vote = 0; vote < 30; vote++) {
            mockMvc.perform(post("/reports/" + reportId + "/helpful"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/reports/" + reportId + "/helpful"))
                .andExpect(status().isTooManyRequests());

        assertThat(reportRepository.findById(reportId).orElseThrow().getHelpful()).isEqualTo(30);
    }

    @Test
    void honeypotAttemptsStillConsumeTheSubmissionBudget() throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            mockMvc.perform(validReportPost("Honeypot Co " + attempt).param("website", "https://spam.example"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(validReportPost("Honeypot Co blocked").param("website", "https://spam.example"))
                .andExpect(status().isTooManyRequests());

        assertThat(reportRepository.countAll()).isZero();
    }

    @Test
    void adminLoginThrottleCannotBeDodgedWithAForwardedForHeader() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        assertThat(session).isNotNull();
        String csrf = String.valueOf(session.getAttribute("OIEP_ADMIN_CSRF"));

        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(post("/admin/login")
                            .session(session)
                            .header("X-Forwarded-For", "198.18.0." + (attempt + 1))
                            .param("_csrf", csrf)
                            .param("username", ADMIN_USER)
                            .param("password", "wrong"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/admin/login")
                        .session(session)
                        .header("X-Forwarded-For", "198.18.0.99")
                        .param("_csrf", csrf)
                        .param("username", ADMIN_USER)
                        .param("password", "wrong"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void authenticatedAdminResponseIsNotCacheable() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/admin/login")).andReturn();
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        String csrf = String.valueOf(session.getAttribute("OIEP_ADMIN_CSRF"));

        MvcResult login = mockMvc.perform(post("/admin/login")
                        .session(session)
                        .param("_csrf", csrf)
                        .param("username", ADMIN_USER)
                        .param("password", "test-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/admin").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
}
