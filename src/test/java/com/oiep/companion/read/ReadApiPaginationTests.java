package com.oiep.companion.read;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keyset pagination over {@code (updated_at, id)}: page_size clamping, has_more/next_cursor
 * agreement, cursor advancement, no duplicates and no reordering.
 */
class ReadApiPaginationTests extends ReadApiEnabledTestSupport {

    @Test
    void pagesWalkEveryItemExactlyOnce() throws Exception {
        List<Long> reportIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            reportIds.add(insertPublished("Paged Co " + i));
        }

        List<String> collectedIds = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        List<Boolean> hasMoreFlags = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        boolean hasMore;
        do {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("page_size", "2");
            if (cursor != null) {
                parameters.put("cursor", cursor);
            }
            MvcResult result = getExperiences(parameters);
            String body = bodyOf(result);
            List<Map<String, Object>> items = itemsOf(result);
            pageSizes.add(items.size());
            hasMore = JsonPath.read(body, "$.has_more");
            hasMoreFlags.add(hasMore);
            String nextCursor = JsonPath.read(body, "$.next_cursor");
            items.forEach(item -> collectedIds.add((String) item.get("id")));
            if (hasMore) {
                assertThat(nextCursor).isNotBlank().isNotEqualTo(cursor);
            } else {
                assertThat(nextCursor).isNull();
            }
            cursor = nextCursor;
            pages++;
            assertThat(pages).isLessThanOrEqualTo(10);
        } while (hasMore);

        assertThat(pageSizes).containsExactly(2, 2, 1);
        assertThat(hasMoreFlags).containsExactly(true, true, false);
        assertThat(collectedIds).hasSize(5).doesNotHaveDuplicates();
        assertThat(collectedIds).containsExactlyElementsOf(reportIds.stream()
                .map(id -> "oiep-r" + id).toList());
    }

    @Test
    void pageSizeLowerBoundIsClampedToOne() throws Exception {
        insertPublished("Clamp One Co");
        insertPublished("Clamp Two Co");

        MvcResult result = getExperiences(Map.of("page_size", "0"));

        assertThat(itemsOf(result)).hasSize(1);
        assertThat(JsonPath.<Boolean>read(bodyOf(result), "$.has_more")).isTrue();
    }

    @Test
    void negativePageSizeIsClampedToOne() throws Exception {
        insertPublished("Clamp Negative Co");

        MvcResult result = getExperiences(Map.of("page_size", "-10"));

        assertThat(itemsOf(result)).hasSize(1);
        assertThat(JsonPath.<Boolean>read(bodyOf(result), "$.has_more")).isFalse();
    }

    @Test
    void pageSizeAboveTheMaximumIsClampedTo500() throws Exception {
        insertPublished("Clamp Large Co");

        MvcResult result = getExperiences(Map.of("page_size", "10000"));

        assertThat(itemsOf(result)).hasSize(1);
        assertThat(JsonPath.<Boolean>read(bodyOf(result), "$.has_more")).isFalse();
    }

    @Test
    void invalidPageSizeReturns400Json() throws Exception {
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", bearerHeader())
                        .param("page_size", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_page_size"));
    }

    @Test
    void absentPageSizeUsesTheConfiguredDefault() throws Exception {
        insertPublished("Default Size Co");

        MvcResult result = getExperiences();

        assertThat(itemsOf(result)).hasSize(1);
        assertThat(JsonPath.<Boolean>read(bodyOf(result), "$.has_more")).isFalse();
    }
}
