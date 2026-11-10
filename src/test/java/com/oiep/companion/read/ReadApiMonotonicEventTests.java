package com.oiep.companion.read;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The event-log watermark must be monotonic: a future timestamp (imported/hand-edited
 * {@code created_at}, restored backup, clock skew) must never seed a companion watermark
 * that hides later hide/delete tombstones from incremental sync.
 */
class ReadApiMonotonicEventTests extends ReadApiEnabledTestSupport {

    @Test
    void backfillClampsFutureCreatedAtToBootTime() throws Exception {
        long reportId = insertReport("Future Co", "published", Instant.parse("2099-01-01T00:00:00Z"));
        // Simulate a report that predates the read API: no event row existed at startup.
        jdbcTemplate.update("DELETE FROM " + ReadApiEventStore.EVENTS_TABLE);

        int inserted = eventStore.backfillExistingReports();
        assertThat(inserted).isEqualTo(1);

        long eventMs = eventMsOf(reportId);
        long now = System.currentTimeMillis();
        assertThat(eventMs).isLessThanOrEqualTo(now + 5_000L);

        // A later moderation tombstone must still be visible to a sync whose watermark is
        // the backfilled event time.
        jdbcTemplate.update("UPDATE reports SET status = 'hidden' WHERE id = ?", reportId);
        long watermark = eventMs;
        var page = eventStore.fetchPage(watermark, null, 10);
        assertThat(page).anyMatch(row -> row.reportId() == reportId && row.eventDeleted());
        assertThat(eventMsOf(reportId)).isGreaterThan(watermark);
    }

    @Test
    void triggerEventsAreStrictlyIncreasing() throws Exception {
        long firstId = insertPublished("Monotonic A");
        long secondId = insertPublished("Monotonic B");
        long first = eventMsOf(firstId);
        long second = eventMsOf(secondId);
        assertThat(second).isGreaterThan(first);

        jdbcTemplate.update("UPDATE reports SET status = 'hidden' WHERE id = ?", firstId);
        long tombstone = eventMsOf(firstId);
        assertThat(tombstone).isGreaterThan(second);
    }

    @Test
    void staleWatermarkStillSeesNewTombstones() throws Exception {
        long reportId = insertPublished("Stale Co");
        jdbcTemplate.update("UPDATE reports SET status = 'hidden' WHERE id = ?", reportId);

        var page = eventStore.fetchPage(0L, null, 100);
        assertThat(page).anyMatch(row -> row.reportId() == reportId && row.eventDeleted());

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", bearerHeader())
                        .param("updated_since", "1970-01-01T00:00:00Z"))
                .andExpect(result -> assertThat(bodyOf(result)).contains("\"deleted\":true"));
    }
}
