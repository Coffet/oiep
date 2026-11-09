package com.oiep.web;

import com.oiep.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class BrowseIntegrationTests extends IntegrationTestSupport {

    @Test
    void browseRendersNormalizedDefaults() throws Exception {
        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"))
                .andExpect(model().attributeExists("results", "total", "page", "totalPages", "q", "round",
                        "outcome", "workMode", "sort", "currentUrl", "activeNav"))
                .andExpect(model().attribute("results", hasSize(0)))
                .andExpect(model().attribute("total", 0L))
                .andExpect(model().attribute("page", 1))
                .andExpect(model().attribute("totalPages", 1))
                .andExpect(model().attribute("q", ""))
                .andExpect(model().attribute("round", ""))
                .andExpect(model().attribute("outcome", ""))
                .andExpect(model().attribute("workMode", ""))
                .andExpect(model().attribute("sort", "recent"))
                .andExpect(model().attribute("currentUrl", "/browse"))
                .andExpect(model().attribute("clearUrl", "/browse"))
                .andExpect(model().attribute("activeNav", "browse"));
    }

    @Test
    void searchAndFiltersNarrowResults() throws Exception {
        reportRepository.insertSeed(sampleReport("Banyan Stack", "technical", "offer", "hybrid", "Kowloon", 9, daysAgo(1)));
        reportRepository.insertSeed(sampleReport("Cedar Labs", "behavioral", "rejection", "remote", "Remote", 2, daysAgo(2)));
        reportRepository.insertSeed(sampleReport("Banyan Stack", "final", "waiting", "onsite", "Central", 5, daysAgo(3)));

        mockMvc.perform(get("/browse").param("round", "technical"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("round", "technical"))
                .andExpect(model().attribute("results", hasSize(1)))
                .andExpect(model().attribute("total", 1L))
                .andExpect(model().attribute("results",
                        contains(hasProperty("company", is("Banyan Stack")))));

        mockMvc.perform(get("/browse").param("q", "banyan"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("q", "banyan"))
                .andExpect(model().attribute("total", 2L))
                .andExpect(model().attribute("results", hasSize(2)));

        mockMvc.perform(get("/browse").param("outcome", "rejection").param("workMode", "remote"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("outcome", "rejection"))
                .andExpect(model().attribute("workMode", "remote"))
                .andExpect(model().attribute("results", hasSize(1)))
                .andExpect(model().attribute("results",
                        contains(hasProperty("company", is("Cedar Labs")))));
    }

    @Test
    void sortHelpfulOrdersByUsefulness() throws Exception {
        reportRepository.insertSeed(sampleReport("Low Helpful", "technical", "offer", "hybrid", "HK", 1, daysAgo(1)));
        reportRepository.insertSeed(sampleReport("High Helpful", "technical", "offer", "hybrid", "HK", 42, daysAgo(9)));
        reportRepository.insertSeed(sampleReport("Mid Helpful", "technical", "offer", "hybrid", "HK", 7, daysAgo(4)));

        mockMvc.perform(get("/browse").param("sort", "helpful"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "helpful"))
                .andExpect(model().attribute("currentUrl", "/browse?sort=helpful"))
                .andExpect(model().attribute("results", contains(
                        hasProperty("company", is("High Helpful")),
                        hasProperty("company", is("Mid Helpful")),
                        hasProperty("company", is("Low Helpful")))));
    }

    @Test
    void unknownFilterValuesAreNormalizedAndNotUsedInQueries() throws Exception {
        insertSampleReport("Banyan Stack", daysAgo(1));
        insertSampleReport("Cedar Labs", daysAgo(2));
        insertSampleReport("Driftwood Systems", daysAgo(3));

        mockMvc.perform(get("/browse")
                        .param("round", "'; DROP TABLE reports; --")
                        .param("outcome", "not-an-outcome")
                        .param("workMode", "on-site")
                        .param("sort", "bogus")
                        .param("page", "not-a-number"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("round", ""))
                .andExpect(model().attribute("outcome", ""))
                .andExpect(model().attribute("workMode", ""))
                .andExpect(model().attribute("sort", "recent"))
                .andExpect(model().attribute("page", 1))
                .andExpect(model().attribute("total", 3L));

        assertThat(reportRepository.countAll()).isEqualTo(3L);
    }

    @Test
    void pageIsClampedToAvailableRange() throws Exception {
        for (int i = 0; i < 11; i++) {
            insertSampleReport("Company " + i, daysAgo(i));
        }

        mockMvc.perform(get("/browse").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("total", 11L))
                .andExpect(model().attribute("page", 2))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(model().attribute("results", hasSize(1)));

        mockMvc.perform(get("/browse").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", 1))
                .andExpect(model().attribute("results", hasSize(10)));
    }

    @Test
    void currentUrlReflectsActiveQuery() throws Exception {
        mockMvc.perform(get("/browse").param("q", "hello world").param("sort", "helpful"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentUrl", "/browse?q=hello%20world&sort=helpful"));
    }
}
