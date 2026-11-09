package com.oiep;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo data must be invisible unless {@code oiep.demo-data=true}, and when enabled it seeds
 * six fictional reports only if the table is empty.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-seed-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=true"
})
@AutoConfigureMockMvc
class DemoDataSeederTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.oiep.repository.ReportRepository reportRepository;

    @Test
    void seedsSixReportsWhenEnabledAndTableIsEmpty() {
        assertThat(reportRepository.countAll()).isEqualTo(6L);
        assertThat(reportRepository.findRecent(10)).hasSize(6);
        assertThat(reportRepository.findRecent(10).get(0).getCompany()).isNotBlank();
    }

    @Test
    void seededReportsAreVisibleOnTheHomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("recentReports", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(model().attribute("recentReports",
                        org.hamcrest.Matchers.contains(
                                hasProperty("company", is("Aster & Finch Technologies")),
                                hasProperty("company", is("Banyan Stack")),
                                hasProperty("company", is("Cobalt Meridian")))))
                .andExpect(model().attribute("pageLang", "en"));
    }
}
