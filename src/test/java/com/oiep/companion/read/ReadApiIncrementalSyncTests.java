package com.oiep.companion.read;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremental sync semantics: the component event log is filtered with a strict
 * {@code event_ms > updated_since} comparison and paged with the same opaque cursor. A full
 * sync (no watermark) returns everything, including tombstones.
 */
class ReadApiIncrementalSyncTests extends ReadApiEnabledTestSupport {

    @Test
    void fullSyncReturnsEveryEvent() throws Exception {
        insertPublished("Full One Co");
        insertPublished("Full Two Co");
        insertPublished("Full Three Co");

        assertThat(itemsOf(getExperiences())).hasSize(3);
    }

    @Test
    void updatedSinceFiltersStrictlyNewerEventsOnly() throws Exception {
        long first = insertPublished("First Co");
        long second = insertPublished("Second Co");
        long third = insertPublished("Third Co");
        setEventMs(first, 1_000L);
        setEventMs(second, 2_000L);
        setEventMs(third, 3_000L);

        List<String> ids = itemsOf(getExperiences(Map.of(
                "updated_since", Instant.ofEpochMilli(1_000L).toString())))
                .stream().map(item -> (String) item.get("id")).toList();

        assertThat(ids).containsExactly("oiep-r" + second, "oiep-r" + third);
    }

    @Test
    void updatedSinceEqualToTheNewestEventReturnsNothing() throws Exception {
        long reportId = insertPublished("Boundary Co");
        setEventMs(reportId, 5_000L);

        MvcResult result = getExperiences(Map.of(
                "updated_since", Instant.ofEpochMilli(5_000L).toString()));

        assertThat(itemsOf(result)).isEmpty();
        assertThat(com.jayway.jsonpath.JsonPath.<Boolean>read(bodyOf(result), "$.has_more"))
                .isFalse();
    }

    @Test
    void updatedSinceAcceptsAnOffsetSpelling() throws Exception {
        long first = insertPublished("Offset First Co");
        long second = insertPublished("Offset Second Co");
        setEventMs(first, 1_000L);
        setEventMs(second, 2_000L);
        String plusEight = Instant.ofEpochMilli(1_000L)
                .atOffset(ZoneOffset.ofHours(8)).toString();

        List<String> ids = itemsOf(getExperiences(Map.of("updated_since", plusEight)))
                .stream().map(item -> (String) item.get("id")).toList();

        assertThat(ids).containsExactly("oiep-r" + second);
    }

    @Test
    void invalidUpdatedSinceReturns400Json() throws Exception {
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", bearerHeader())
                        .param("updated_since", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_updated_since"));
    }

    @Test
    void cursorContinuesAnIncrementalWindow() throws Exception {
        long first = insertPublished("Cursor First Co");
        long second = insertPublished("Cursor Second Co");
        long third = insertPublished("Cursor Third Co");
        setEventMs(first, 1_000L);
        setEventMs(second, 2_000L);
        setEventMs(third, 3_000L);
        String watermark = Instant.ofEpochMilli(1_000L).toString();

        MvcResult firstPage = getExperiences(Map.of(
                "updated_since", watermark,
                "page_size", "1"));
        List<Map<String, Object>> firstItems = itemsOf(firstPage);
        assertThat(firstItems).hasSize(1);
        assertThat(firstItems.get(0).get("id")).isEqualTo("oiep-r" + second);

        String cursor = com.jayway.jsonpath.JsonPath.read(
                bodyOf(firstPage), "$.next_cursor");
        assertThat(cursor).isNotBlank();

        MvcResult secondPage = getExperiences(Map.of(
                "updated_since", watermark,
                "page_size", "1",
                "cursor", cursor));
        List<Map<String, Object>> secondItems = itemsOf(secondPage);
        assertThat(secondItems).hasSize(1);
        assertThat(secondItems.get(0).get("id")).isEqualTo("oiep-r" + third);
        assertThat(com.jayway.jsonpath.JsonPath.<Boolean>read(
                bodyOf(secondPage), "$.has_more")).isFalse();
    }

    @Test
    void invalidCursorReturns400Json() throws Exception {
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", bearerHeader())
                        .param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_cursor"));
    }
}
