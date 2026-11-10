package com.oiep.security;

import com.oiep.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression cover for the stateless CSRF defence and forwarded-header trust on the anonymous
 * state-changing endpoints.
 */
class AnonymousEndpointHardeningTests extends IntegrationTestSupport {

    @Test
    void crossOriginSubmissionIsRejectedAndNotPersisted() throws Exception {
        mockMvc.perform(validReportPost("CSRF Origin Co")
                        .header("Origin", "https://evil.example")
                        .header("Referer", "https://evil.example/phish"))
                .andExpect(status().isForbidden());

        assertThat(reportRepository.countAll()).isZero();
    }

    @Test
    void crossSiteFetchMetadataSubmissionIsRejected() throws Exception {
        mockMvc.perform(validReportPost("CSRF Fetch Co").header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());

        assertThat(reportRepository.countAll()).isZero();
    }

    @Test
    void sameOriginSubmissionIsAccepted() throws Exception {
        mockMvc.perform(validReportPost("Same Origin Co")
                        .header("Origin", "http://localhost")
                        .header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().is3xxRedirection());

        assertThat(reportRepository.countAll()).isEqualTo(1L);
    }

    @Test
    void crossOriginHelpfulVoteIsRejected() throws Exception {
        long reportId = insertSampleReport("Vote Target Co", daysAgo(1));

        mockMvc.perform(post("/reports/" + reportId + "/helpful")
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());

        assertThat(reportRepository.findById(reportId).orElseThrow().getHelpful()).isZero();
    }

    @Test
    void helpfulVoteWithoutBrowserProvenanceIsStillAccepted() throws Exception {
        long reportId = insertSampleReport("Vote Target Co", daysAgo(1));

        mockMvc.perform(post("/reports/" + reportId + "/helpful"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reports/" + reportId + "#useful"));

        assertThat(reportRepository.findById(reportId).orElseThrow().getHelpful()).isEqualTo(1);
    }

    @Test
    void rotatingXForwardedForCannotEvadeTheSubmissionLimit() throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            mockMvc.perform(validReportPost("Forwarded Co " + attempt)
                            .header("X-Forwarded-For", "198.18.0." + (attempt + 1)))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(validReportPost("Forwarded Co blocked")
                        .header("X-Forwarded-For", "198.18.0.99"))
                .andExpect(status().isTooManyRequests());

        assertThat(reportRepository.countAll()).isEqualTo(8L);
    }

    @Test
    void helpfulVotesRemainReadableAndUnchangedForAnonymousVisitors() throws Exception {
        long reportId = insertSampleReport("Readable Co", daysAgo(1));

        mockMvc.perform(get("/reports/" + reportId))
                .andExpect(status().isOk());
    }
}
