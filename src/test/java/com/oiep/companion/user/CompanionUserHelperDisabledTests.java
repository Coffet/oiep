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
 * Shipped default configuration: the companion is disabled, so the public page must be
 * exactly the page OIEP had before the component existed — no extension bean, no fragment
 * in the model, no CSP {@code frame-src} directive.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-companion-user-disabled-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password"
})
@AutoConfigureMockMvc
class CompanionUserHelperDisabledTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void noCompanionBeansAreRegisteredWhenTheComponentIsDisabled() {
        assertThat(applicationContext.getBeanNamesForType(CompanionUserHelperExtension.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PageExtension.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CspContributor.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CompanionUserHelperModelAdvice.class)).isEmpty();
    }

    @Test
    void homePageRendersUnchangedWithoutTheHelper() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("pageExtensions", hasSize(0)))
                .andExpect(model().attributeDoesNotExist("companionUserHelper"))
                .andExpect(header().string("Content-Security-Policy", not(containsString("frame-src"))));
    }
}
