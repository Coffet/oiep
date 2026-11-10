package com.oiep.companion.read;

import com.jayway.jsonpath.JsonPath;
import com.oiep.domain.Report;
import com.oiep.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A second enabled context with non-default read-API configuration: it proves that the
 * {@code oiep.companion.read-api.*} properties are wired end to end, both the configured
 * default page size and the optional {@code vibe-proxy} difficulty adapter.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-read-api-configured-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.companion.enabled=true",
        "oiep.companion.read-api.enabled=true",
        "oiep.companion.read-api.token=test-read-token-0123456789abcdef",
        "oiep.companion.read-api.page-size=2",
        "oiep.companion.read-api.difficulty=HARD",
        "oiep.companion.read-api.difficulty-mode=vibe-proxy"
})
@AutoConfigureMockMvc
class ReadApiConfiguredContextTests {

    private static final String TOKEN = "test-read-token-0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReadApiService readApiService;

    @BeforeEach
    void resetReadApiState() {
        jdbcTemplate.update("DELETE FROM reports");
        jdbcTemplate.update("DELETE FROM " + ReadApiEventStore.EVENTS_TABLE);
    }

    @Test
    void configuredPageSizeBecomesTheDefaultPageSize() throws Exception {
        insertReport("Configured Page One Co", "warm");
        insertReport("Configured Page Two Co", "warm");
        insertReport("Configured Page Three Co", "warm");

        assertThat(readApiService.defaultPageSize()).isEqualTo(2);

        String body = mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((List<?>) JsonPath.read(body, "$.items")).hasSize(2);
        assertThat(JsonPath.<Boolean>read(body, "$.has_more")).isTrue();
    }

    @Test
    void configuredVibeProxyModeDerivesDifficultyFromTheVibeField() throws Exception {
        insertReport("Warm Vibe Co", "warm");
        insertReport("Cold Vibe Co", "cold");
        insertReport("No Vibe Co", "");

        String body = mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        Map<String, String> difficultyByCompany = items.stream().collect(Collectors.toMap(
                item -> (String) item.get("company"),
                item -> (String) item.get("difficulty")));

        assertThat(difficultyByCompany)
                .containsEntry("Warm Vibe Co", "EASY")
                .containsEntry("Cold Vibe Co", "HARD")
                .containsEntry("No Vibe Co", "MODERATE");
    }

    private void insertReport(String company, String vibe) {
        Report report = new Report();
        report.setCompany(company);
        report.setRole("Backend Engineer");
        report.setRound("technical");
        report.setWorkMode("hybrid");
        report.setLocation("Hong Kong");
        report.setOutcome("offer");
        report.setHeardBack("week");
        report.setQuestionTypes(List.of("algorithm"));
        report.setVibe(vibe);
        report.setStory("A context-configuration note for the read API adapter test.");
        report.setCreatedAt(Instant.now().minusSeconds(3_600).toString());
        report.setStatus("published");
        reportRepository.insertSeed(report);
    }
}
