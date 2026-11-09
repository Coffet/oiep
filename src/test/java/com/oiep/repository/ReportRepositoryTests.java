package com.oiep.repository;

import com.oiep.IntegrationTestSupport;
import com.oiep.domain.Report;
import com.oiep.domain.ReportForm;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRepositoryTests extends IntegrationTestSupport {

    @Test
    void insertAndFindByIdRoundTripsAllFields() {
        ReportForm form = new ReportForm();
        form.setCompany("  Banyan Stack  ");
        form.setRole("Backend Engineer");
        form.setRound("technical");
        form.setWorkMode("hybrid");
        form.setLocation("Kowloon, HK");
        form.setOutcome("offer");
        form.setHeardBack("week");
        form.setQuestionTypes(List.of("algorithm", "ALGORITHM", "not-a-tag", " systemdesign "));
        form.setSalary("HKD 45k / month");
        form.setVibe("warm");
        form.setStory("The loop was clear and the feedback arrived within a week, with specifics.");
        form.setConfirm(true);

        long id = reportRepository.insert(form);
        Report report = reportRepository.findById(id).orElseThrow();

        assertThat(report.getId()).isEqualTo(id);
        assertThat(report.getCompany()).isEqualTo("Banyan Stack");
        assertThat(report.getRole()).isEqualTo("Backend Engineer");
        assertThat(report.getRound()).isEqualTo("technical");
        assertThat(report.getWorkMode()).isEqualTo("hybrid");
        assertThat(report.getLocation()).isEqualTo("Kowloon, HK");
        assertThat(report.getOutcome()).isEqualTo("offer");
        assertThat(report.getHeardBack()).isEqualTo("week");
        assertThat(report.getQuestionTypes()).containsExactly("algorithm", "systemdesign");
        assertThat(report.getSalary()).isEqualTo("HKD 45k / month");
        assertThat(report.getVibe()).isEqualTo("warm");
        assertThat(report.getStory()).isEqualTo("The loop was clear and the feedback arrived within a week, with specifics.");
        assertThat(report.getHelpful()).isZero();
        assertThat(report.getStatus()).isEqualTo("published");
        assertThat(report.getCreatedAtLabel()).matches("\\d{2}\\.\\d{2}\\.\\d{2}");
    }

    @Test
    void findRecentReturnsNewestFirst() {
        reportRepository.insertSeed(sampleReport("Oldest Co", daysAgo(20)));
        reportRepository.insertSeed(sampleReport("Newest Co", daysAgo(1)));
        reportRepository.insertSeed(sampleReport("Middle Co", daysAgo(10)));

        List<Report> recent = reportRepository.findRecent(2);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).getCompany()).isEqualTo("Newest Co");
        assertThat(recent.get(1).getCompany()).isEqualTo("Middle Co");
    }

    @Test
    void findMatchingAppliesFiltersAndPagination() {
        reportRepository.insertSeed(sampleReport("A Co", "technical", "offer", "hybrid", "HK", 0, daysAgo(1)));
        reportRepository.insertSeed(sampleReport("B Co", "technical", "offer", "remote", "HK", 0, daysAgo(2)));
        reportRepository.insertSeed(sampleReport("C Co", "behavioral", "rejection", "hybrid", "HK", 0, daysAgo(3)));

        assertThat(reportRepository.countMatching("", "", "", "")).isEqualTo(3L);
        assertThat(reportRepository.countMatching("", "technical", "", "")).isEqualTo(2L);
        assertThat(reportRepository.countMatching("", "", "rejection", "hybrid")).isEqualTo(1L);
        assertThat(reportRepository.countMatching("c co", "", "", "")).isEqualTo(1L);

        assertThat(reportRepository.findMatching("", "", "", "", "recent", 2, 0)).hasSize(2);
        assertThat(reportRepository.findMatching("", "", "", "", "recent", 2, 2)).hasSize(1);
        assertThat(reportRepository.findMatching("", "", "", "", "recent", 0, -5)).hasSize(1);
    }

    @Test
    void searchEscapesSqlLikeWildcards() {
        reportRepository.insertSeed(sampleReport("100% Remote Co", "technical", "offer", "remote", "Remote", 0, daysAgo(1)));
        reportRepository.insertSeed(sampleReport("Plain Co", "technical", "offer", "hybrid", "Hong Kong", 0, daysAgo(2)));

        assertThat(reportRepository.countMatching("%", "", "", "")).isEqualTo(1L);
        assertThat(reportRepository.countMatching("100%", "", "", "")).isEqualTo(1L);
        assertThat(reportRepository.countMatching("_", "", "", "")).isZero();
        assertThat(reportRepository.countMatching("' OR 1=1 --", "", "", "")).isZero();
        assertThat(reportRepository.countAll()).isEqualTo(2L);
    }

    @Test
    void incrementHelpfulOnlyForExistingReports() {
        long id = insertSampleReport("Banyan Stack", daysAgo(1));

        assertThat(reportRepository.incrementHelpful(id)).isTrue();
        assertThat(reportRepository.incrementHelpful(id)).isTrue();
        assertThat(reportRepository.findById(id).orElseThrow().getHelpful()).isEqualTo(2);

        assertThat(reportRepository.incrementHelpful(999999L)).isFalse();
    }

    @Test
    void questionTypesAreSanitizedWhenSeeding() {
        Report seed = sampleReport("Banyan Stack", daysAgo(1));
        seed.setQuestionTypes(List.of("ALGORITHM", "bogus", "domain", "domain", ""));
        long id = reportRepository.insertSeed(seed);

        assertThat(reportRepository.findById(id).orElseThrow().getQuestionTypes())
                .containsExactly("algorithm", "domain");
    }

    @Test
    void excerptIsPlainTextWordBoundedAndShort() {
        Report shortReport = sampleReport("Short Co", daysAgo(1));
        shortReport.setStory("Line one.\nLine two.");
        long shortId = reportRepository.insertSeed(shortReport);
        assertThat(reportRepository.findById(shortId).orElseThrow().getExcerpt())
                .isEqualTo("Line one. Line two.");

        String longStory = "This is a long interview note. ".repeat(20);
        Report longReport = sampleReport("Long Co", daysAgo(2));
        longReport.setStory(longStory);
        long longId = reportRepository.insertSeed(longReport);
        Report stored = reportRepository.findById(longId).orElseThrow();
        assertThat(stored.getExcerpt().length()).isLessThanOrEqualTo(170);
        assertThat(stored.getExcerpt()).doesNotContain("\n");
        assertThat(stored.getStory()).startsWith("This is a long interview note.");
    }

    @Test
    void findByIdReturnsEmptyForMissingReport() {
        assertThat(reportRepository.findById(123456L)).isEmpty();
        assertThat(reportRepository.existsPublishedById(123456L)).isFalse();
    }

    @Test
    void seedKeepsExplicitCreatedAtAndHelpfulCount() {
        Report seed = sampleReport("Seeded Co", daysAgo(5));
        seed.setCreatedAt(Instant.parse("2026-01-02T03:04:05Z").toString());
        seed.setHelpful(17);
        long id = reportRepository.insertSeed(seed);

        Report stored = reportRepository.findById(id).orElseThrow();
        assertThat(stored.getHelpful()).isEqualTo(17);
        assertThat(stored.getCreatedAt()).isEqualTo("2026-01-02T03:04:05Z");
    }
}
