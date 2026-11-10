package com.oiep.companion;

import com.oiep.IntegrationTestSupport;
import com.oiep.config.CspContributor;
import com.oiep.config.WebConfig;
import com.oiep.web.ui.PageExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The optional AI companion component must be completely inert with the default
 * configuration: no beans, no routes, no page fragments, no CSP changes, no markup.
 *
 * <p>This is the contract that makes the component safe to add to an existing install and
 * keep removed by configuration alone.</p>
 */
class CompanionDisabledIntegrationTests extends IntegrationTestSupport {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @Autowired
    private MessageSource messageSource;

    @Test
    void noComponentBeansAreRegistered() {
        assertThat(applicationContext.getBeansOfType(PageExtension.class))
                .as("no page extension may exist while the component is disabled")
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(CspContributor.class))
                .as("no CSP contributor may exist while the component is disabled")
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(CompanionBootstrap.class)).isEmpty();
    }

    @Test
    void homePageHasNoCompanionMarkupAndTheOriginalCsp() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("companion"))))
                .andExpect(header().string("Content-Security-Policy", WebConfig.contentSecurityPolicy()));
        assertThat(WebConfig.contentSecurityPolicy()).doesNotContain("frame-src");
    }

    @Test
    void componentRoutesAreAbsent() throws Exception {
        mockMvc.perform(get("/api/v1/experiences"))
                .andExpect(status().isNotFound());
        // /admin/** is core-owned: unmapped admin paths still hit the core admin interceptor and
        // redirect an anonymous caller to the login page (and 404 for an authenticated admin).
        // No component handler exists, which is what keeps the component removable.
        mockMvc.perform(get("/admin/assistant"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/login"));
    }

    @Test
    void companionMessageBundlesResolveInBothLanguages() {
        String english = messageSource.getMessage("companion.helper.title", null, Locale.ENGLISH);
        String chinese = messageSource.getMessage("companion.helper.title", null, Locale.forLanguageTag("zh-HK"));
        assertThat(english).isNotBlank();
        assertThat(chinese).isNotBlank();
        assertThat(CJK.matcher(english).find()).isFalse();
        assertThat(CJK.matcher(chinese).find()).isTrue();
        assertThat(english).contains("?");
        assertThat(chinese).isNotEqualTo(english);
    }
}
