package com.oiep.companion.user;

import com.oiep.config.CspContributor;
import com.oiep.web.ui.PageExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Fail closed: an unusable widget URL must register nothing at all. A javascript:/data: URL
 * would be a script-injection vector and a URL with credentials would leak them through the
 * rendered {@code src}; both are rejected by the shared settings validator, so the component
 * disappears instead of rendering an unsafe iframe.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-companion-user-invalid-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.companion.enabled=true",
        "oiep.companion.user-helper.widget-url=javascript:alert(document.domain)"
})
@AutoConfigureMockMvc
class CompanionUserHelperInvalidWidgetUrlTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unsafeWidgetUrlRegistersNothing() {
        assertThat(applicationContext.getBeanNamesForType(CompanionUserHelperExtension.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PageExtension.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CompanionUserHelperModelAdvice.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CspContributor.class).values())
                .allSatisfy(contributor -> assertThat(contributor.directives())
                        .noneMatch(directive -> directive.contains("frame-src")));
    }

    @Test
    void homePageStillRendersWithoutAnyHelperMarkup() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("pageExtensions", hasSize(0)))
                .andExpect(model().attributeDoesNotExist("companionUserHelper"))
                .andExpect(header().string("Content-Security-Policy", not(containsString("frame-src"))));
    }
}
