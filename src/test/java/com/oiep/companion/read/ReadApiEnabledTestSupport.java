package com.oiep.companion.read;

import com.jayway.jsonpath.JsonPath;
import com.oiep.domain.Report;
import com.oiep.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for read-API tests that need the companion component, the read API and its
 * triggers enabled.
 *
 * <p>Each application context gets its own fresh SQLite file under {@code target/test-data}.
 * Before every test both the reports table and the component event table are emptied, so every
 * test starts from a clean, deterministic sync state. (Deleting reports fires the component's
 * delete trigger, hence the event table is emptied second.)</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-read-api-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.companion.enabled=true",
        "oiep.companion.read-api.enabled=true",
        "oiep.companion.read-api.token=test-read-token-0123456789abcdef",
        "oiep.companion.read-api.page-size=200",
        "oiep.companion.read-api.difficulty=MODERATE",
        "oiep.companion.read-api.difficulty-mode=constant"
})
@AutoConfigureMockMvc
public abstract class ReadApiEnabledTestSupport {

    /** The read token configured for this context (32 chars, not a documented placeholder). */
    public static final String TOKEN = "test-read-token-0123456789abcdef";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ReportRepository reportRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ReadApiEventStore eventStore;

    @BeforeEach
    void resetReadApiState() {
        jdbcTemplate.update("DELETE FROM reports");
        jdbcTemplate.update("DELETE FROM " + ReadApiEventStore.EVENTS_TABLE);
    }

    protected static String bearerHeader() {
        return "Bearer " + TOKEN;
    }

    protected long insertPublished(String company) {
        return insertReport(company, "published", daysAgo(1));
    }

    protected long insertPublished(String company, String round, String outcome, String workMode,
                                   String location, String vibe, List<String> questionTypes,
                                   String story) {
        return insertReport(company, round, outcome, workMode, location, vibe, questionTypes,
                story, "published", daysAgo(1));
    }

    protected long insertReport(String company, String status, Instant createdAt) {
        return insertReport(company, "technical", "offer", "hybrid", "Hong Kong", "warm",
                List.of("algorithm", "behavioral"),
                "A detailed first-hand note about the interview process at " + company + ".",
                status, createdAt);
    }

    protected long insertReport(String company, String round, String outcome, String workMode,
                                String location, String vibe, List<String> questionTypes,
                                String story, String status, Instant createdAt) {
        Report report = new Report();
        report.setCompany(company);
        report.setRole("Backend Engineer");
        report.setRound(round);
        report.setWorkMode(workMode);
        report.setLocation(location);
        report.setOutcome(outcome);
        report.setHeardBack("week");
        report.setQuestionTypes(new ArrayList<>(questionTypes));
        report.setSalary("HKD 40k / month");
        report.setVibe(vibe);
        report.setStory(story);
        report.setHelpful(0);
        report.setCreatedAt(createdAt.toString());
        report.setStatus(status);
        return reportRepository.insertSeed(report);
    }

    protected Instant daysAgo(long days) {
        return Instant.now().minusSeconds(days * 24L * 60L * 60L);
    }

    /** Performs an authorized first/continuation page request expecting HTTP 200. */
    protected MvcResult getExperiences(Map<String, String> queryParameters) throws Exception {
        MockHttpServletRequestBuilder request = get(ReadApiController.EXPERIENCES_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader());
        queryParameters.forEach(request::param);
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
    }

    protected MvcResult getExperiences() throws Exception {
        return getExperiences(Map.of());
    }

    /** Parses the {@code items} array of a read-API response. */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> itemsOf(MvcResult result) throws Exception {
        return (List<Map<String, Object>>) (List<?>) JsonPath.read(bodyOf(result), "$.items");
    }

    protected static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The epoch-millisecond event time currently stored for a report. */
    protected long eventMsOf(long reportId) {
        Long eventMs = jdbcTemplate.queryForObject(
                "SELECT event_ms FROM " + ReadApiEventStore.EVENTS_TABLE + " WHERE report_id = ?",
                Long.class, reportId);
        return eventMs == null ? -1L : eventMs;
    }

    /** Overwrites a report's event time to make incremental ordering tests deterministic. */
    protected void setEventMs(long reportId, long eventMs) {
        jdbcTemplate.update("UPDATE " + ReadApiEventStore.EVENTS_TABLE
                + " SET event_ms = ? WHERE report_id = ?", eventMs, reportId);
    }

    /** Deleted flag currently stored for a report (1 = tombstone event). */
    protected int deletedFlagOf(long reportId) {
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM " + ReadApiEventStore.EVENTS_TABLE + " WHERE report_id = ?",
                Integer.class, reportId);
        return deleted == null ? -1 : deleted;
    }

    /** Names of the component-owned database objects present in the current test database. */
    protected List<String> componentDatabaseObjects() {
        return jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE name LIKE 'companion_%' ORDER BY name",
                String.class);
    }
}
