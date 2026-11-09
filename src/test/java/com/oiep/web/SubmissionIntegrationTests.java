package com.oiep.web;

import com.oiep.IntegrationTestSupport;
import com.oiep.domain.Report;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class SubmissionIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private Environment environment;

    @Test
    void successfulPostRedirectLocationHasNoSessionId() throws Exception {
        assertThat(environment.getProperty("server.servlet.session.tracking-modes"))
                .as("sessions must be tracked by cookie only, never URL rewriting")
                .isEqualTo("cookie");

        MvcResult postResult = mockMvc.perform(validReportPost("No Session Id In Url Co"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectUrl = postResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).matches("/reports/\\d+");
        assertThat(redirectUrl).doesNotContain(";jsessionid");
    }

    @Test
    void validSubmissionRedirectsPersistsAndSetsFlash() throws Exception {
        MvcResult postResult = mockMvc.perform(validReportPost("Banyan Stack"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("justPosted", true))
                .andReturn();

        String redirectUrl = postResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).matches("/reports/\\d+");
        long id = Long.parseLong(redirectUrl.substring("/reports/".length()));

        Report persisted = reportRepository.findById(id).orElse(null);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getCompany()).isEqualTo("Banyan Stack");
        assertThat(persisted.getQuestionTypes()).containsExactly("algorithm", "systemdesign");
        assertThat(persisted.getCreatedAtLabel()).matches("\\d{2}\\.\\d{2}\\.\\d{2}");
        assertThat(persisted.getStatus()).isEqualTo("published");
        assertThat(persisted.getHelpful()).isZero();

        MockHttpSession session = (MockHttpSession) postResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get(redirectUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(model().attribute("justPosted", true))
                .andExpect(model().attribute("report", hasProperty("company", is("Banyan Stack"))));
    }

    @Test
    void invalidSubmissionReturns422WithIndexModelAndNoInsert() throws Exception {
        mockMvc.perform(post("/reports")
                        .param("company", "")
                        .param("role", "")
                        .param("round", "not-a-round")
                        .param("outcome", "")
                        .param("story", "too short")
                        .param("confirm", "false")
                        .param("questionTypes", "not-a-tag"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("reportForm", "recentReports", "currentUrl", "activeNav"))
                .andExpect(model().attribute("currentUrl", "/"))
                .andExpect(model().attribute("activeNav", "home"))
                .andExpect(model().attributeDoesNotExist("submitBlocked"))
                .andExpect(model().attributeHasFieldErrors("reportForm",
                        "company", "role", "round", "outcome", "story", "confirm"))
                .andExpect(model().attributeHasFieldErrors("reportForm", "questionTypes[0]"));

        assertThat(reportRepository.countAll()).isZero();
    }

    @Test
    void honeypotSubmissionIsSilentlyRedirectedAndNotSaved() throws Exception {
        mockMvc.perform(validReportPost("Spam Co").param("website", "https://spam.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(reportRepository.countAll()).isZero();
    }

    @Test
    void rateLimiterBlocksNinthSubmission() throws Exception {
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(validReportPost("Rate Limited Co " + i))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(validReportPost("Rate Limited Co blocked"))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("submitBlocked", true))
                .andExpect(model().attribute("currentUrl", "/"))
                .andExpect(model().attribute("activeNav", "home"))
                .andExpect(model().attribute("recentReports", hasSize(3)));

        assertThat(reportRepository.countAll()).isEqualTo(8L);
    }

    @Test
    void helpfulIsCountedOncePerSession() throws Exception {
        long id = insertSampleReport("Banyan Stack", daysAgo(1));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/reports/" + id + "/helpful").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reports/" + id + "#useful"));

        mockMvc.perform(get("/reports/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("alreadyHelpful", true))
                .andExpect(model().attribute("report", hasProperty("helpful", is(1))));

        // A second vote in the same session must not increment again.
        mockMvc.perform(post("/reports/" + id + "/helpful").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reports/" + id + "#useful"));

        assertThat(reportRepository.findById(id).orElseThrow().getHelpful()).isEqualTo(1);
    }

    @Test
    void helpfulForMissingReportReturns404() throws Exception {
        mockMvc.perform(post("/reports/999999/helpful").session(new MockHttpSession()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 404));
    }

    @Test
    void helpfulForMissingReportDoesNotCreateSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/reports/999999/helpful"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .as("an anonymous request to a missing report must not allocate a session")
                .noneMatch(header -> header.startsWith("JSESSIONID="));
    }
}
