package com.oiep.web;

import com.oiep.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ReportPageIntegrationTests extends IntegrationTestSupport {

    @Test
    void reportPageRendersContractModel() throws Exception {
        long id = insertSampleReport("Banyan Stack", daysAgo(1));

        mockMvc.perform(get("/reports/" + id))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(model().attributeExists("report", "alreadyHelpful", "currentUrl", "activeNav", "pageLang"))
                .andExpect(model().attribute("report", hasProperty("company", is("Banyan Stack"))))
                .andExpect(model().attribute("report", hasProperty("id", is(id))))
                .andExpect(model().attribute("alreadyHelpful", false))
                .andExpect(model().attribute("currentUrl", "/reports/" + id))
                .andExpect(model().attribute("activeNav", "browse"));
    }

    @Test
    void missingReportReturnsStyled404() throws Exception {
        mockMvc.perform(get("/reports/999999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 404))
                .andExpect(model().attribute("error", "Not Found"))
                .andExpect(model().attribute("currentUrl", "/"))
                .andExpect(model().attribute("pageLang", "en"));
    }

    @Test
    void missingReport404KeepsChinesePageLanguage() throws Exception {
        mockMvc.perform(get("/reports/999999").cookie(new Cookie("OIEP_LANG", "zh-HK")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("pageLang", "zh-HK"));
    }

    @Test
    void nonNumericReportIdReturns404() throws Exception {
        mockMvc.perform(get("/reports/not-a-number"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/definitely-not-a-page"))
                .andExpect(status().isNotFound());
    }

    @Test
    void errorRouteRendersErrorView() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().is5xxServerError())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 500))
                .andExpect(model().attribute("currentUrl", "/"));
    }
}
