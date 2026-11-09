package com.oiep;

import com.oiep.domain.Report;
import com.oiep.repository.ReportRepository;
import com.oiep.service.AdminLoginRateLimiter;
import com.oiep.service.SubmissionRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared integration-test setup.
 *
 * <p>Each application context gets a fresh SQLite file under {@code target/test-data}
 * (resolved per context through {@code ${random.uuid}}), and every test starts from an empty
 * reports table and an empty rate limiter. JSP files are not required: MockMvc resolves view
 * names without rendering them.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-test-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ReportRepository reportRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected SubmissionRateLimiter rateLimiter;

    @Autowired
    protected AdminLoginRateLimiter adminLoginRateLimiter;

    @Autowired
    protected ApplicationContext applicationContext;

    @BeforeEach
    void resetDatabaseAndRateLimiter() {
        jdbcTemplate.update("DELETE FROM reports");
        rateLimiter.reset();
        adminLoginRateLimiter.reset();
    }

    protected static Report sampleReport(String company, String round, String outcome, String workMode,
                                         String location, int helpful, Instant createdAt) {
        Report report = new Report();
        report.setCompany(company);
        report.setRole("Backend Engineer");
        report.setRound(round);
        report.setWorkMode(workMode);
        report.setLocation(location);
        report.setOutcome(outcome);
        report.setHeardBack("week");
        report.setQuestionTypes(new ArrayList<>(List.of("algorithm", "behavioral")));
        report.setSalary("HKD 40k / month");
        report.setVibe("warm");
        report.setStory("A detailed first-hand note about the interview process at " + company
                + ". The rounds were explained in advance and the questions matched the role.");
        report.setHelpful(helpful);
        report.setCreatedAt(createdAt.toString());
        report.setStatus("published");
        return report;
    }

    protected static Report sampleReport(String company, Instant createdAt) {
        return sampleReport(company, "technical", "offer", "hybrid", "Hong Kong", 0, createdAt);
    }

    protected long insertSampleReport(String company, Instant createdAt) {
        return reportRepository.insertSeed(sampleReport(company, createdAt));
    }

    protected MockHttpServletRequestBuilder validReportPost(String company) {
        return post("/reports")
                .param("company", company)
                .param("role", "Backend Engineer")
                .param("round", "technical")
                .param("workMode", "hybrid")
                .param("location", "Hong Kong")
                .param("outcome", "offer")
                .param("heardBack", "week")
                .param("questionTypes", "algorithm", "systemdesign")
                .param("salary", "HKD 45k / month")
                .param("vibe", "warm")
                .param("story", "Two coding rounds and a system design round. The interviewers were "
                        + "prepared and the questions matched the job description closely.")
                .param("confirm", "true");
    }

    protected Instant daysAgo(long days) {
        return Instant.now().minusSeconds(days * 24L * 60L * 60L);
    }
}
