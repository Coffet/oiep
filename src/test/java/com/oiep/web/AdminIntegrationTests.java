package com.oiep.web;

import com.oiep.IntegrationTestSupport;
import com.oiep.service.AdminAuth;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AdminIntegrationTests extends IntegrationTestSupport {

    private static final String ADMIN_USER = "test-admin";
    private static final String ADMIN_PASSWORD = "test-password";

    @Test
    void anonymousAdminRequestRedirectsToLoginWithoutCreatingASession() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void loginPageCreatesCsrfTokenAndReportsConfiguration() throws Exception {
        MockHttpSession session = openLoginSession();

        mockMvc.perform(get("/admin/login").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attribute("adminConfigured", true))
                .andExpect(model().attribute("adminCsrfToken", notNullValue()))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void wrongPasswordIsRejectedWithGenericError() throws Exception {
        MockHttpSession session = openLoginSession();

        mockMvc.perform(post("/admin/login").session(session)
                        .param("_csrf", csrf(session))
                        .param("username", ADMIN_USER)
                        .param("password", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attribute("loginError", "admin.login.error.invalid"));
    }

    @Test
    void successfulLoginReachesDashboardWithStats() throws Exception {
        insertSampleReport("Dashboard Co", daysAgo(1));
        MockHttpSession session = login();

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(model().attributeExists(
                        "reports", "totalNotes", "publishedNotes", "hiddenNotes",
                        "helpfulTotal", "recentNotes", "adminCsrfToken", "pageLang"))
                .andExpect(model().attribute("totalNotes", 1L))
                .andExpect(model().attribute("publishedNotes", 1L))
                .andExpect(model().attribute("hiddenNotes", 0L));
    }

    @Test
    void hideAndRestoreChangesPublicVisibility() throws Exception {
        long reportId = insertSampleReport("Hidden Co", daysAgo(1));
        MockHttpSession session = login();
        String csrf = csrf(session);

        mockMvc.perform(post("/admin/reports/" + reportId + "/hide").session(session)
                        .param("_csrf", csrf)
                        .param("returnTo", "/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        org.assertj.core.api.Assertions.assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo("hidden");
        mockMvc.perform(get("/reports/" + reportId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/reports/" + reportId).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/report"));

        mockMvc.perform(post("/admin/reports/" + reportId + "/restore").session(session)
                        .param("_csrf", csrf)
                        .param("returnTo", "/admin"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/reports/" + reportId)).andExpect(status().isOk());
    }

    @Test
    void deleteRequiresConfirmationAndRemovesTheReport() throws Exception {
        long reportId = insertSampleReport("Delete Co", daysAgo(1));
        MockHttpSession session = login();
        String csrf = csrf(session);

        mockMvc.perform(get("/admin/reports/" + reportId + "/delete").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/delete"));

        mockMvc.perform(post("/admin/reports/" + reportId + "/delete").session(session)
                        .param("_csrf", csrf))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        org.assertj.core.api.Assertions.assertThat(reportRepository.findById(reportId)).isEmpty();
    }

    @Test
    void mutatingActionRejectsMissingCsrfToken() throws Exception {
        long reportId = insertSampleReport("Protected Co", daysAgo(1));
        MockHttpSession session = login();

        mockMvc.perform(post("/admin/reports/" + reportId + "/hide").session(session)
                        .param("_csrf", "wrong-token")
                        .param("returnTo", "/admin"))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo("published");
    }

    @Test
    void repeatedBadLoginsAreRateLimited() throws Exception {
        MockHttpSession session = openLoginSession();
        String csrf = csrf(session);

        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(post("/admin/login").session(session)
                            .param("_csrf", csrf)
                            .param("username", ADMIN_USER)
                            .param("password", "wrong"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/admin/login").session(session)
                        .param("_csrf", csrf)
                        .param("username", ADMIN_USER)
                        .param("password", "wrong"))
                .andExpect(status().isTooManyRequests())
                .andExpect(model().attribute("loginError", "admin.login.error.rateLimited"));
    }

    private MockHttpSession openLoginSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        org.assertj.core.api.Assertions.assertThat(csrf(session)).isNotBlank();
        return session;
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession loginSession = openLoginSession();
        MvcResult result = mockMvc.perform(post("/admin/login").session(loginSession)
                        .param("_csrf", csrf(loginSession))
                        .param("username", ADMIN_USER)
                        .param("password", ADMIN_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(authenticated).isNotNull();
        org.assertj.core.api.Assertions.assertThat(authenticated).isNotSameAs(loginSession);
        return authenticated;
    }

    private static String csrf(MockHttpSession session) {
        Object token = session.getAttribute(AdminAuth.CSRF_SESSION_KEY);
        return token instanceof String value ? value : "";
    }
}
