package com.oiep.companion.read;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One page of the read-only experiences API.
 *
 * <p>{@code next_cursor} is non-null exactly when {@code has_more} is true, and always advances
 * past the last item of this page. When {@code has_more} is false the caller has the complete
 * result set for the requested window and should not send {@code next_cursor} back.</p>
 *
 * @param items      items in ascending {@code (updated_at, id)} order
 * @param nextCursor opaque continuation cursor, or null on the last page
 * @param hasMore    true when more items exist beyond this page
 */
public record ExperiencesPage(
        List<ExperienceItem> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore) {
}
