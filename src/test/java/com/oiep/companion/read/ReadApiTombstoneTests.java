package com.oiep.companion.read;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tombstone propagation: hidden and deleted reports must never be served as full records; they
 * are emitted as minimal {@code {id, updated_at, deleted:true}} markers so the companion can
 * remove them from its mirror. Restored reports come back as full items.
 */
class ReadApiTombstoneTests extends ReadApiEnabledTestSupport {

    @Test
    void seededHiddenReportIsATombstone() throws Exception {
        long reportId = insertReport("Hidden Seed Co", "hidden", daysAgo(2));

        MvcResult result = getExperiences();
        List<Map<String, Object>> items = itemsOf(result);

        assertThat(items).hasSize(1);
        Map<String, Object> item = items.get(0);
        assertThat(item.get("id")).isEqualTo("oiep-r" + reportId);
        assertThat(item.get("deleted")).isEqualTo(true);
        assertThat(Instant.parse((String) item.get("updated_at"))).isNotNull();
        assertThat(item).doesNotContainKeys("company", "role_title", "overall_experience",
                "rounds", "schema_version", "submitted_at");
    }

    @Test
    void tombstonesExposeExactlyTheDocumentedTombstoneKeys() throws Exception {
        insertReport("Hidden Keys Co", "hidden", daysAgo(2));

        Map<String, Object> item = itemsOf(getExperiences()).get(0);

        assertThat(item.keySet())
                .as("a tombstone carries only the documented removal fields")
                .containsExactlyInAnyOrder("id", "updated_at", "deleted");
        assertThat(item).doesNotContainKey("tombstone");
    }

    @Test
    void hidingAPublishedReportTurnsItIntoATombstone() throws Exception {
        long reportId = insertPublished("Hide Me Co");

        assertThat(itemsOf(getExperiences()).get(0).get("deleted")).isEqualTo(false);
        assertThat(reportRepository.updateStatus(reportId, "hidden")).isTrue();

        MvcResult result = getExperiences();
        Map<String, Object> item = itemsOf(result).get(0);
        assertThat(item.get("deleted")).isEqualTo(true);
        assertThat(item.get("id")).isEqualTo("oiep-r" + reportId);
        assertThat(bodyOf(result)).doesNotContain("Hide Me Co");
    }

    @Test
    void restoringAHiddenReportBringsTheFullItemBack() throws Exception {
        long reportId = insertReport("Restored Co", "hidden", daysAgo(3));

        assertThat(itemsOf(getExperiences()).get(0).get("deleted")).isEqualTo(true);
        assertThat(reportRepository.updateStatus(reportId, "published")).isTrue();

        Map<String, Object> item = itemsOf(getExperiences()).get(0);
        assertThat(item.get("deleted")).isEqualTo(false);
        assertThat(item.get("company")).isEqualTo("Restored Co");
        assertThat(item.get("schema_version")).isEqualTo("interview-experience/v1");
        assertThat(item.get("overall_experience")).isInstanceOf(String.class);
    }

    @Test
    void deletingTheReportRowEmitsATombstone() throws Exception {
        long reportId = insertPublished("Delete Me Co");

        assertThat(itemsOf(getExperiences()).get(0).get("deleted")).isEqualTo(false);
        assertThat(jdbcTemplate.update("DELETE FROM reports WHERE id = ?", reportId)).isEqualTo(1);

        MvcResult result = getExperiences();
        List<Map<String, Object>> items = itemsOf(result);
        assertThat(items).hasSize(1);
        Map<String, Object> item = items.get(0);
        assertThat(item.get("id")).isEqualTo("oiep-r" + reportId);
        assertThat(item.get("deleted")).isEqualTo(true);
        assertThat(Instant.parse((String) item.get("updated_at"))).isNotNull();
        assertThat(bodyOf(result)).doesNotContain("Delete Me Co");
    }

    @Test
    void hiddenReportTextIsNeverExposed() throws Exception {
        String secretStory = "Sensitive details that must never reach the mirror.";
        long reportId = insertReport("Confidential Corp", "technical", "offer", "onsite",
                "Hong Kong", "warm", List.of("algorithm"), secretStory, "hidden", daysAgo(1));

        MvcResult result = getExperiences();

        assertThat(itemsOf(result).get(0).get("id")).isEqualTo("oiep-r" + reportId);
        assertThat(bodyOf(result))
                .doesNotContain("Confidential Corp")
                .doesNotContain(secretStory);
    }

    @Test
    void incrementalSyncSeesHidingAsATombstone() throws Exception {
        long reportId = insertPublished("Incremental Hide Co");
        long firstEventMs = eventMsOf(reportId);
        String firstWatermark = Instant.ofEpochMilli(firstEventMs).toString();

        reportRepository.updateStatus(reportId, "hidden");
        setEventMs(reportId, firstEventMs + 1_000L);

        MvcResult result = mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", bearerHeader())
                        .param("updated_since", firstWatermark))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Incremental Hide Co"))))
                .andReturn();

        List<Map<String, Object>> items = itemsOf(result);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("deleted")).isEqualTo(true);
        assertThat(items.get(0).get("id")).isEqualTo("oiep-r" + reportId);
    }
}
