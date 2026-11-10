package com.oiep.companion.read;

import com.oiep.companion.CompanionConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The component-owned report event log that drives the read API's full and incremental syncs.
 *
 * <p>The log is populated without touching a single line of core application code: three
 * SQLite triggers on {@code reports} record a {@code (report_id, event_ms, deleted)} row for
 * every insert, every moderation-status change and every delete. {@code deleted} is derived
 * from the report's current status, so a hidden report becomes a tombstone and a restored
 * report becomes a full item again. Only when the read API is enabled does
 * {@link ReadApiSchemaInitializer} create the table and triggers; the script
 * {@code scripts/companion-uninstall.sql} drops them again.</p>
 *
 * <p>Keeping the log separate from the reports table means core moderation code, JSPs and the
 * repository stay exactly as before, and the read API can reconstruct what changed since a
 * companion watermark. Every statement is a prepared statement with bound parameters.</p>
 */
@Component
@Conditional(CompanionConditions.ReadApi.class)
public class ReadApiEventStore {

    /** Component-owned event table (dropped by scripts/companion-uninstall.sql). */
    public static final String EVENTS_TABLE = "companion_report_events";
    /** Trigger name for report inserts. */
    public static final String INSERT_TRIGGER = "companion_report_events_ai";
    /** Trigger name for moderation-status changes. */
    public static final String UPDATE_TRIGGER = "companion_report_events_au";
    /** Trigger name for report deletes. */
    public static final String DELETE_TRIGGER = "companion_report_events_ad";
    /** Keyset-pagination index over the event ordering used by the sync pages. */
    public static final String ORDER_INDEX = "companion_report_events_order";

    private static final Logger log = LoggerFactory.getLogger(ReadApiEventStore.class);

    /** Epoch milliseconds in SQLite (UTC; julianday is the documented portable spelling). */
    private static final String RAW_NOW_MS =
            "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)";

    /**
     * Event time that never moves backwards relative to the existing log.
     *
     * <p>Using the wall clock alone lets a future timestamp (hand-edited/imported
     * {@code created_at}, restored backup, clock skew) enter the log. A companion that has
     * seen that future watermark would then ignore every later real event — including
     * hide/delete tombstones — and silently keep serving hidden content. The monotonic
     * guard keeps each trigger event strictly above the current maximum.</p>
     */
    private static final String NOW_MS =
            "MAX(" + RAW_NOW_MS + ", (SELECT COALESCE(MAX(event_ms), 0) + 1 FROM " + EVENTS_TABLE + "))";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + EVENTS_TABLE + " ("
                    + "report_id INTEGER PRIMARY KEY, "
                    + "event_ms  INTEGER NOT NULL, "
                    + "deleted   INTEGER NOT NULL)";

    /**
     * Covers the read API's total order {@code (event_ms, report_id)} so a page is an index
     * range scan plus a bounded limit rather than a full sort of the event log.
     */
    private static final String CREATE_ORDER_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS " + ORDER_INDEX + " ON " + EVENTS_TABLE
                    + " (event_ms, report_id)";

    private static final String CREATE_INSERT_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS " + INSERT_TRIGGER + " AFTER INSERT ON reports "
                    + "BEGIN "
                    + "INSERT OR REPLACE INTO " + EVENTS_TABLE
                    + " (report_id, event_ms, deleted) VALUES (NEW.id, " + NOW_MS + ", "
                    + "CASE WHEN NEW.status = 'published' THEN 0 ELSE 1 END); "
                    + "END";

    private static final String CREATE_UPDATE_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS " + UPDATE_TRIGGER + " AFTER UPDATE OF status ON reports "
                    + "WHEN OLD.status IS NOT NEW.status "
                    + "BEGIN "
                    + "INSERT OR REPLACE INTO " + EVENTS_TABLE
                    + " (report_id, event_ms, deleted) VALUES (NEW.id, " + NOW_MS + ", "
                    + "CASE WHEN NEW.status = 'published' THEN 0 ELSE 1 END); "
                    + "END";

    private static final String CREATE_DELETE_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS " + DELETE_TRIGGER + " AFTER DELETE ON reports "
                    + "BEGIN "
                    + "INSERT OR REPLACE INTO " + EVENTS_TABLE
                    + " (report_id, event_ms, deleted) VALUES (OLD.id, " + NOW_MS + ", 1); "
                    + "END";

    private static final String BACKFILL_SQL =
            "INSERT OR IGNORE INTO " + EVENTS_TABLE + " (report_id, event_ms, deleted) "
                    + "VALUES (?, ?, ?)";

    private static final String PAGE_SELECT =
            "SELECT e.report_id AS event_report_id, e.event_ms AS event_ms, "
                    + "e.deleted AS event_deleted, "
                    + "r.id AS report_id, r.company AS company, r.role AS role, "
                    + "r.round AS round, r.work_mode AS work_mode, r.location AS location, "
                    + "r.outcome AS outcome, r.question_types AS question_types, "
                    + "r.vibe AS vibe, r.story AS story, r.created_at AS created_at, "
                    + "r.status AS status "
                    + "FROM " + EVENTS_TABLE + " e "
                    + "LEFT JOIN reports r ON r.id = e.report_id";

    private static final int BACKFILL_BATCH_SIZE = 500;

    private static final RowMapper<ReadApiRow> ROW_MAPPER = ReadApiEventStore::toRow;

    private final JdbcTemplate jdbcTemplate;

    public ReadApiEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates the event table and its triggers idempotently. Called once at startup when the
     * read API is enabled; safe to call again.
     */
    public void ensureEventSchema() {
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        jdbcTemplate.execute(CREATE_ORDER_INDEX_SQL);
        jdbcTemplate.execute(CREATE_INSERT_TRIGGER_SQL);
        jdbcTemplate.execute(CREATE_UPDATE_TRIGGER_SQL);
        jdbcTemplate.execute(CREATE_DELETE_TRIGGER_SQL);
    }

    /**
     * Seeds one event per report that does not have one yet (reports that existed before the
     * read API was enabled). Existing events are never overwritten: a report already tracked
     * keeps its most recent trigger timestamp, so a restart cannot move a companion watermark
     * backwards or replay old updates.
     *
     * @return the number of events inserted
     */
    public int backfillExistingReports() {
        long before = countEvents();
        List<Object[]> parameters = new ArrayList<>();
        long fallbackEventMs = System.currentTimeMillis();
        int withoutCreatedAt = 0;
        int futureClamped = 0;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, created_at, status FROM reports ORDER BY id");
        for (Map<String, Object> row : rows) {
            Object rawId = row.get("id");
            if (!(rawId instanceof Number number)) {
                continue;
            }
            Long eventMs = ReadApiTimestamps.epochMillisOrNull(asText(row.get("created_at")));
            if (eventMs == null) {
                // External boundary (hand-edited/imported database): keep the report visible
                // rather than dropping it, but do not let it block startup.
                eventMs = fallbackEventMs;
                withoutCreatedAt++;
            } else if (eventMs > fallbackEventMs) {
                // Never seed the event log with a time in the future: a companion that
                // adopted it as its watermark would ignore every later real event.
                eventMs = fallbackEventMs;
                futureClamped++;
            }
            int deleted = "published".equals(asText(row.get("status"))) ? 0 : 1;
            parameters.add(new Object[]{number.longValue(), eventMs, deleted});
        }
        for (int start = 0; start < parameters.size(); start += BACKFILL_BATCH_SIZE) {
            List<Object[]> batch = parameters.subList(
                    start, Math.min(start + BACKFILL_BATCH_SIZE, parameters.size()));
            jdbcTemplate.batchUpdate(BACKFILL_SQL, batch);
        }
        if (withoutCreatedAt > 0) {
            log.warn("AI companion read API backfill: {} report(s) had no parsable created_at; "
                    + "using the boot time as their event time", withoutCreatedAt);
        }
        if (futureClamped > 0) {
            log.warn("AI companion read API backfill: {} report(s) had a future created_at; "
                    + "clamped to the boot time so later moderation events stay visible to "
                    + "incremental syncs", futureClamped);
        }
        return (int) Math.max(0L, countEvents() - before);
    }

    /**
     * Reads one page of events after the given position, joined with the current report row.
     *
     * @param updatedSinceMs incremental watermark (events strictly newer), or null for full sync
     * @param after          exclusive cursor position, or null for the first page
     * @param limit          maximum rows to return (caller asks for page size + 1)
     */
    public List<ReadApiRow> fetchPage(Long updatedSinceMs, ReadApiCursor after, int limit) {
        StringBuilder sql = new StringBuilder(PAGE_SELECT);
        List<Object> arguments = new ArrayList<>(4);
        if (after != null) {
            // A continuation page resumes exactly after the emitted (event_ms, report_id)
            // position; ordering is total because report_id is unique and monotonic.
            sql.append(" WHERE (e.event_ms > ? OR (e.event_ms = ? AND e.report_id > ?))");
            arguments.add(after.eventMs());
            arguments.add(after.eventMs());
            arguments.add(after.reportId());
        } else if (updatedSinceMs != null) {
            sql.append(" WHERE e.event_ms > ?");
            arguments.add(updatedSinceMs);
        }
        sql.append(" ORDER BY e.event_ms ASC, e.report_id ASC LIMIT ?");
        arguments.add(Math.max(1, limit));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, arguments.toArray());
    }

    /** Number of tracked report events (test/diagnostic helper; never body data). */
    public long countEvents() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + EVENTS_TABLE, Long.class);
        return count == null ? 0L : count;
    }

    private static ReadApiRow toRow(ResultSet resultSet, int rowNumber) throws SQLException {
        long reportId = resultSet.getLong("event_report_id");
        long eventMs = resultSet.getLong("event_ms");
        boolean eventDeleted = resultSet.getInt("event_deleted") != 0;
        long joinedReportId = resultSet.getLong("report_id");
        boolean reportPresent = !resultSet.wasNull();
        if (!reportPresent) {
            return new ReadApiRow(reportId, eventMs, eventDeleted, false,
                    null, null, null, null, null, null, null, null, null, null, null);
        }
        return new ReadApiRow(
                reportId,
                eventMs,
                eventDeleted,
                joinedReportId > 0L,
                resultSet.getString("company"),
                resultSet.getString("role"),
                resultSet.getString("round"),
                resultSet.getString("work_mode"),
                resultSet.getString("location"),
                resultSet.getString("outcome"),
                resultSet.getString("question_types"),
                resultSet.getString("vibe"),
                resultSet.getString("story"),
                resultSet.getString("created_at"),
                resultSet.getString("status"));
    }

    private static String asText(Object value) {
        return value == null ? "" : value.toString();
    }
}
