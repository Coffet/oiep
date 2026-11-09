package com.oiep.web;

import com.oiep.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LanguageIntegrationTests extends IntegrationTestSupport {

    @Test
    void englishByDefault() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageLang", "en"));
    }

    @Test
    void switchingToChineseSetsCookieAndRedirectsBack() throws Exception {
        MvcResult result = mockMvc.perform(get("/language/zh-HK").param("returnTo", "/browse?q=backend"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/browse?q=backend"))
                .andExpect(cookie().exists("OIEP_LANG"))
                .andReturn();

        Cookie localeCookie = result.getResponse().getCookie("OIEP_LANG");
        assertThat(localeCookie).isNotNull();
        assertThat(localeCookie.getPath()).isEqualTo("/");
        assertThat(localeCookie.isHttpOnly()).isTrue();
        assertThat(localeCookie.getMaxAge()).isEqualTo(365 * 24 * 60 * 60);

        mockMvc.perform(get("/browse").cookie(localeCookie))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageLang", "zh-HK"));
    }

    @Test
    void languageCodeIsCaseAndSeparatorTolerant() throws Exception {
        mockMvc.perform(get("/language/ZH_hk").param("returnTo", "/about"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about"));
    }

    @Test
    void unsafeReturnToIsRejected() throws Exception {
        mockMvc.perform(get("/language/en").param("returnTo", "https://evil.example/phish"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/language/en").param("returnTo", "//evil.example/phish"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/language/en").param("returnTo", "/ok\r\nLocation: https://evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/language/en"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void dotSegmentsInReturnToAreRejected() throws Exception {
        for (String unsafe : new String[]{"/../etc", "/foo/../..", "/a/./b", "/.."}) {
            mockMvc.perform(get("/language/en").param("returnTo", unsafe))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }
    }
}
