package com.oiep.companion;

import com.oiep.config.CspContributor;
import com.oiep.config.WebConfig;
import com.oiep.web.ui.PageExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Master switch only: the component is enabled but no sub-feature has a usable
 * configuration. Everything must still fail closed (no routes, no fragments, no CSP change)
 * and the application must keep working.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-enabled-noop-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.companion.enabled=true"
})
@AutoConfigureMockMvc
class CompanionEnabledNoopIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void noSubFeatureBeansExistWhenConfigurationIsIncomplete() {
        assertThat(applicationContext.getBeansOfType(PageExtension.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(CspContributor.class)).isEmpty();
    }

    @Test
    void pagesKeepTheOriginalCspAndNoComponentMarkup() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", WebConfig.contentSecurityPolicy()));
        mockMvc.perform(get("/api/v1/experiences")).andExpect(status().isNotFound());
    }
}
