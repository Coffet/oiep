package com.oiep.security;

import com.oiep.IntegrationTestSupport;
import com.oiep.service.AdminAuth;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF tokens are only accepted from the request body: a token carried in the URL query
 * string would leak through history, proxy logs and {@code Referer} headers, so it is
 * rejected even when valid.
 */
class AdminCsrfQueryStringTests extends IntegrationTestSupport {

    private static final String ADMIN_USER = "test-admin";
    private static final String ADMIN_PASSWORD = "test-password";

    @Test
    void loginRejectsAValidTokenSuppliedInTheQueryString() throws Exception {
        MockHttpSession session = openLoginSession();
        String token = csrf(session);

        mockMvc.perform(post("/admin/login")
                        .session(session)
                        .queryParam("_csrf", token)
                        .param("username", ADMIN_USER)
                        .param("password", ADMIN_PASSWORD))
                .andExpect(status().isForbidden());

        assertThat(session.getAttribute(AdminAuth.AUTHENTICATED_SESSION_KEY)).isNull();
    }

    @Test
    void mutationRejectsAValidTokenSuppliedInTheQueryString() throws Exception {
        long reportId = insertSampleReport("CSRF Query Co", daysAgo(1));
        MockHttpSession authenticated = login();
        String token = csrf(authenticated);

        mockMvc.perform(post("/admin/reports/" + reportId + "/hide")
                        .session(authenticated)
                        .queryParam("_csrf", token)
                        .param("returnTo", "/admin"))
                .andExpect(status().isForbidden());

        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus()).isEqualTo("published");
    }

    @Test
    void loginStillAcceptsTheTokenFromTheBody() throws Exception {
        MockHttpSession session = openLoginSession();
        String token = csrf(session);

        mockMvc.perform(post("/admin/login")
                        .session(session)
                        .param("_csrf", token)
                        .param("username", ADMIN_USER)
                        .param("password", ADMIN_PASSWORD))
                .andExpect(status().is3xxRedirection());
    }

    private MockHttpSession openLoginSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession loginSession = openLoginSession();
        MvcResult result = mockMvc.perform(post("/admin/login")
                        .session(loginSession)
                        .param("_csrf", csrf(loginSession))
                        .param("username", ADMIN_USER)
                        .param("password", ADMIN_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        return authenticated;
    }

    private static String csrf(MockHttpSession session) {
        Object token = session.getAttribute(AdminAuth.CSRF_SESSION_KEY);
        return token instanceof String value ? value : "";
    }
}
