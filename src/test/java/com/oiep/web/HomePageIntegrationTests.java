package com.oiep.web;

import com.oiep.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class HomePageIntegrationTests extends IntegrationTestSupport {

    @Test
    void homePageRendersWithContractModel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("reportForm", "recentReports", "currentUrl", "activeNav", "pageLang"))
                .andExpect(model().attribute("currentUrl", "/"))
                .andExpect(model().attribute("activeNav", "home"))
                .andExpect(model().attribute("pageLang", "en"))
                .andExpect(model().attribute("recentReports", hasSize(0)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void recentReportsAreNewestFirstAndCappedAtThree() throws Exception {
        insertSampleReport("Oldest Co", daysAgo(30));
        insertSampleReport("Newest Co", daysAgo(1));
        insertSampleReport("Middle Co", daysAgo(10));
        insertSampleReport("Second Newest Co", daysAgo(3));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("recentReports", hasSize(3)))
                .andExpect(model().attribute("recentReports", contains(
                        hasProperty("company", is("Newest Co")),
                        hasProperty("company", is("Second Newest Co")),
                        hasProperty("company", is("Middle Co")))));
    }
}
