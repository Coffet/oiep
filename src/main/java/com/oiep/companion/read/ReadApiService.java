package com.oiep.companion.read;

import com.oiep.companion.CompanionConditions;
import com.oiep.companion.CompanionProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only query service behind {@code GET /api/v1/experiences}.
 *
 * <p>Serves the companion's corpus sync in two modes:</p>
 * <ul>
 *   <li><strong>full sync</strong> ({@code updated_since} absent) — every event the component
 *       has ever recorded, including tombstones;</li>
 *   <li><strong>incremental sync</strong> ({@code updated_since} present) — only events whose
 *       component timestamp is strictly newer than the watermark, so an item is never skipped
 *       between two syncs.</li>
 * </ul>
 *
 * <p>Pagination is keyset-based over the totally ordered
 * {@code (event_ms, report_id)} position. {@code page_size} is clamped to 1..500 (default from
 * {@code oiep.companion.read-api.page-size}, itself 200 unless the operator changed it). The
 * response carries an opaque cursor exactly when {@code has_more} is true.</p>
 */
@Service
@Conditional(CompanionConditions.ReadApi.class)
public class ReadApiService {

    /** Smallest accepted page size. */
    static final int MIN_PAGE_SIZE = 1;
    /** Largest accepted page size (also the companion client's own ceiling). */
    static final int MAX_PAGE_SIZE = 500;
    /** Page size used when the configuration value is unusable. */
    static final int FALLBACK_PAGE_SIZE = 200;

    private final ReadApiEventStore eventStore;
    private final InterviewExperienceAdapter adapter;
    private final int defaultPageSize;

    public ReadApiService(ReadApiEventStore eventStore, CompanionProperties properties) {
        this.eventStore = eventStore;
        this.adapter = new InterviewExperienceAdapter(properties);
        this.defaultPageSize = sanitiseDefaultPageSize(properties.getReadApi().getPageSize());
    }

    /**
     * Produces one page.
     *
     * @param updatedSince raw ISO-8601 watermark, or null/blank for a full sync
     * @param rawCursor    opaque continuation cursor, or null/blank for the first page
     * @param rawPageSize  requested page size, or null/blank for the configured default
     */
    public ExperiencesPage page(String updatedSince, String rawCursor, String rawPageSize) {
        int pageSize = resolvePageSize(rawPageSize);
        ReadApiCursor after = decodeCursor(rawCursor);
        Long updatedSinceMs = after == null ? parseUpdatedSince(updatedSince) : null;

        // One extra row tells us whether another page follows without a second query.
        List<ReadApiRow> rows = eventStore.fetchPage(updatedSinceMs, after, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<ReadApiRow> pageRows = hasMore ? new ArrayList<>(rows.subList(0, pageSize)) : rows;
        List<ExperienceItem> items = new ArrayList<>(pageRows.size());
        for (ReadApiRow row : pageRows) {
            items.add(adapter.adapt(row));
        }
        String nextCursor = null;
        if (hasMore) {
            ReadApiRow last = pageRows.get(pageRows.size() - 1);
            nextCursor = new ReadApiCursor(last.eventMs(), last.reportId()).encode();
        }
        return new ExperiencesPage(items, nextCursor, hasMore);
    }

    /** The configured default page size after sanitising (visible for diagnostics/tests). */
    public int defaultPageSize() {
        return defaultPageSize;
    }

    private int resolvePageSize(String rawPageSize) {
        String candidate = ReadApiTimestamps.trimToEmpty(rawPageSize);
        if (candidate.isEmpty()) {
            return defaultPageSize;
        }
        int requested;
        try {
            requested = Integer.parseInt(candidate);
        } catch (NumberFormatException ex) {
            throw ReadApiRequestException.badRequest("invalid_page_size");
        }
        return Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, requested));
    }

    private static ReadApiCursor decodeCursor(String rawCursor) {
        String candidate = ReadApiTimestamps.trimToEmpty(rawCursor);
        return candidate.isEmpty() ? null : ReadApiCursor.decode(candidate);
    }

    private static Long parseUpdatedSince(String rawUpdatedSince) {
        String candidate = ReadApiTimestamps.trimToEmpty(rawUpdatedSince);
        if (candidate.isEmpty()) {
            return null;
        }
        Long epochMillis = ReadApiTimestamps.epochMillisOrNull(candidate);
        if (epochMillis == null) {
            throw ReadApiRequestException.badRequest("invalid_updated_since");
        }
        return epochMillis;
    }

    private static int sanitiseDefaultPageSize(int configuredPageSize) {
        if (configuredPageSize >= MIN_PAGE_SIZE && configuredPageSize <= MAX_PAGE_SIZE) {
            return configuredPageSize;
        }
        return FALLBACK_PAGE_SIZE;
    }
}
