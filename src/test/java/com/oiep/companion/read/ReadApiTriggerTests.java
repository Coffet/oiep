package com.oiep.companion.read;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The component-owned SQLite triggers and the startup backfill. These are what make moderation
 * changes and deletes propagate to the companion without touching any core report code.
 */
class ReadApiTriggerTests extends ReadApiEnabledTestSupport {

    private static final Instant BACKFILL_CREATED_AT = Instant.parse("2025-03-04T05:06:07Z");

    @Test
    void componentDatabaseObjectsAreCreatedOnlyWithTheReadApiEnabled() {
        List<String> objects = componentDatabaseObjects();

        assertThat(objects).contains(
                ReadApiEventStore.EVENTS_TABLE,
                ReadApiEventStore.ORDER_INDEX,
                ReadApiEventStore.INSERT_TRIGGER,
                ReadApiEventStore.UPDATE_TRIGGER,
                ReadApiEventStore.DELETE_TRIGGER);
    }

    @Test
    void schemaCreationIsIdempotent() {
        eventStore.ensureEventSchema();
        eventStore.ensureEventSchema();

        assertThat(componentDatabaseObjects())
                .contains(ReadApiEventStore.ORDER_INDEX,
                        ReadApiEventStore.INSERT_TRIGGER,
                        ReadApiEventStore.UPDATE_TRIGGER,
                        ReadApiEventStore.DELETE_TRIGGER);
    }

    @Test
    void insertWritesAPublishedEvent() {
        long reportId = insertPublished("Trigger Insert Co");

        assertThat(eventMsOf(reportId)).isPositive();
        assertThat(deletedFlagOf(reportId)).isZero();
    }

    @Test
    void hidingWritesATombstoneEvent() {
        long reportId = insertPublished("Trigger Hide Co");

        reportRepository.updateStatus(reportId, "hidden");

        assertThat(deletedFlagOf(reportId)).isEqualTo(1);
    }

    @Test
    void restoringWritesAPublishedEventAgain() {
        long reportId = insertPublished("Trigger Restore Co");
        reportRepository.updateStatus(reportId, "hidden");

        reportRepository.updateStatus(reportId, "published");

        assertThat(deletedFlagOf(reportId)).isZero();
    }

    @Test
    void settingTheSameStatusAgainDoesNotWriteANewEvent() {
        long reportId = insertPublished("Trigger Noop Co");
        long originalEventMs = eventMsOf(reportId);

        reportRepository.updateStatus(reportId, "published");

        assertThat(eventMsOf(reportId)).isEqualTo(originalEventMs);
    }

    @Test
    void unrelatedUpdatesDoNotWriteAnEvent() {
        long reportId = insertPublished("Trigger Helpful Co");
        long originalEventMs = eventMsOf(reportId);

        reportRepository.incrementHelpful(reportId);

        assertThat(eventMsOf(reportId)).isEqualTo(originalEventMs);
    }

    @Test
    void deleteWritesATombstoneThatOutlivesTheReportRow() {
        long reportId = insertPublished("Trigger Delete Co");

        jdbcTemplate.update("DELETE FROM reports WHERE id = ?", reportId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE id = ?", Integer.class, reportId)).isZero();
        assertThat(deletedFlagOf(reportId)).isEqualTo(1);
    }

    @Test
    void backfillSeedsReportsThatPredateTheComponent() {
        long published = insertReport("Backfill Published Co", "published", BACKFILL_CREATED_AT);
        long hidden = insertReport("Backfill Hidden Co", "hidden", BACKFILL_CREATED_AT);
        jdbcTemplate.update("DELETE FROM " + ReadApiEventStore.EVENTS_TABLE);

        int backfilled = eventStore.backfillExistingReports();

        assertThat(backfilled).isEqualTo(2);
        assertThat(eventMsOf(published)).isEqualTo(BACKFILL_CREATED_AT.toEpochMilli());
        assertThat(deletedFlagOf(published)).isZero();
        assertThat(eventMsOf(hidden)).isEqualTo(BACKFILL_CREATED_AT.toEpochMilli());
        assertThat(deletedFlagOf(hidden)).isEqualTo(1);
    }

    @Test
    void backfillIsIdempotentAndNeverOverwritesANewerEvent() {
        long reportId = insertPublished("Backfill Keep Co");
        setEventMs(reportId, 123_456_789L);

        int firstRun = eventStore.backfillExistingReports();
        int secondRun = eventStore.backfillExistingReports();

        assertThat(firstRun).isZero();
        assertThat(secondRun).isZero();
        assertThat(eventMsOf(reportId)).isEqualTo(123_456_789L);
    }

    @Test
    void backfillUsesBootTimeWhenCreatedAtIsUnparsable() {
        long reportId = insertPublished("Backfill Garbage Date Co");
        jdbcTemplate.update("UPDATE reports SET created_at = 'not-a-date' WHERE id = ?", reportId);
        jdbcTemplate.update("DELETE FROM " + ReadApiEventStore.EVENTS_TABLE);
        long startedAt = System.currentTimeMillis();

        int backfilled = eventStore.backfillExistingReports();

        assertThat(backfilled).isEqualTo(1);
        assertThat(eventMsOf(reportId)).isGreaterThanOrEqualTo(startedAt - 1_000L);
        assertThat(deletedFlagOf(reportId)).isZero();
    }

    @Test
    void deletedFlagIsStoredAsAnIntegerZeroOrOne() {
        long reportId = insertPublished("Backfill Integer Co");
        setEventMs(reportId, 999L);

        Map<String, Object> event = jdbcTemplate.queryForMap(
                "SELECT event_ms, deleted FROM " + ReadApiEventStore.EVENTS_TABLE
                        + " WHERE report_id = ?", reportId);

        assertThat(((Number) event.get("event_ms")).longValue()).isEqualTo(999L);
        assertThat(((Number) event.get("deleted")).intValue()).isZero();
    }
}
