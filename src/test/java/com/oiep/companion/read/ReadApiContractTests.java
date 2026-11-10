package com.oiep.companion.read;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The emitted item contract: field mapping, required companion fields, schema version, the
 * fixed single-round shape, omission of blank optional fields, the exact set of serialized keys
 * and the response hardening headers. Also covers the JSON-only content negotiation: the read
 * namespace must never answer a request with OIEP's HTML error page, whatever the
 * {@code Accept} header says.
 */
class ReadApiContractTests extends ReadApiEnabledTestSupport {

    /** The exact wire keys of a full {@code interview-experience/v1} item for the test row. */
    private static final Set<String> DOCUMENTED_FULL_ITEM_KEYS = Set.of(
            "id", "submitted_at", "updated_at", "deleted", "schema_version", "company",
            "role_title", "location", "work_mode", "outcome", "difficulty", "rounds",
            "overall_experience");

    @Test
    void mapsAPublishedReportIntoTheCompanionContract() throws Exception {
        long reportId = insertPublished("Banyan Stack", "systemdesign", "withdrew", "remote",
                "Kowloon, HK", "warm", List.of("systemdesign", "domain"),
                "Two system design rounds with detailed feedback and a take-home exercise.");
        String storedCreatedAt = reportRepository.findById(reportId).orElseThrow().getCreatedAt();

        MvcResult result = getExperiences();
        List<Map<String, Object>> items = itemsOf(result);

        assertThat(items).hasSize(1);
        Map<String, Object> item = items.get(0);
        assertThat(item.get("id")).isEqualTo("oiep-r" + reportId);
        assertThat(item.get("submitted_at")).isEqualTo(storedCreatedAt);
        assertThat(Instant.parse((String) item.get("updated_at"))).isNotNull();
        assertThat(item.get("deleted")).isEqualTo(false);
        assertThat(item.get("schema_version")).isEqualTo("interview-experience/v1");
        assertThat(item.get("company")).isEqualTo("Banyan Stack");
        assertThat(item.get("role_title")).isEqualTo("Backend Engineer");
        assertThat(item.get("location")).isEqualTo("Kowloon, HK");
        assertThat(item.get("work_mode")).isEqualTo("REMOTE");
        assertThat(item.get("outcome")).isEqualTo("WITHDRAWN");
        assertThat(item.get("difficulty")).isEqualTo("MODERATE");
        assertThat(item.get("overall_experience")).isEqualTo(
                "Two system design rounds with detailed feedback and a take-home exercise.");

        List<?> rounds = (List<?>) item.get("rounds");
        assertThat(rounds).hasSize(1);
        Map<?, ?> round = (Map<?, ?>) rounds.get(0);
        assertThat(round.get("index")).isEqualTo(1);
        assertThat(round.get("type")).isEqualTo("SYSTEM_DESIGN");
        assertThat(round.get("format")).isEqualTo("UNKNOWN");
        assertThat(round.get("topics")).isEqualTo(List.of("systemdesign", "domain"));
        assertThat(round.containsKey("notes")).isFalse();
    }

    @Test
    void companionRequiredFieldsArePresentAndNonEmpty() throws Exception {
        insertPublished("Required Fields Co");

        Map<String, Object> item = itemsOf(getExperiences()).get(0);

        for (String required : Set.of("company", "role_title", "outcome", "difficulty",
                "overall_experience", "schema_version", "id")) {
            assertThat(item.get(required)).as("required field %s", required)
                    .isInstanceOf(String.class);
            assertThat((String) item.get(required)).as("required field %s", required)
                    .isNotBlank();
        }
        List<?> rounds = (List<?>) item.get("rounds");
        assertThat(rounds).isNotNull().isNotEmpty();
    }

    @Test
    void itemsExposeExactlyTheDocumentedContractKeys() throws Exception {
        insertPublished("Documented Keys Co");

        Map<String, Object> item = itemsOf(getExperiences()).get(0);

        assertThat(item.keySet())
                .as("serialized item keys must be exactly the documented oiep-read-api/v1 keys")
                .containsExactlyInAnyOrderElementsOf(DOCUMENTED_FULL_ITEM_KEYS);
        // Regression: a convenience accessor on the record must not leak as an extra JSON
        // property (the wire contract has no "tombstone" field).
        assertThat(item).doesNotContainKey("tombstone");
    }

    @Test
    void blankOptionalFieldsAreOmittedNotSentAsNull() throws Exception {
        insertPublished("Blank Optional Co", "technical", "offer", "", "", "warm",
                List.of(), "A short but valid note for the blank-field test case.");

        Map<String, Object> item = itemsOf(getExperiences()).get(0);

        assertThat(item).doesNotContainKey("location");
        assertThat(item).doesNotContainKey("work_mode");
        assertThat(item).doesNotContainKey("role_level");
        assertThat(item).doesNotContainKey("employment_type");
        assertThat(item).doesNotContainKey("interview_date");
    }

    @Test
    void everyMappedOutcomeIsRepresentedByItsContractEnum() throws Exception {
        insertPublished("Offer Co", "technical", "offer", "onsite", "Hong Kong", "warm",
                List.of("algorithm"), "Offer outcome mapping test note with enough text.");
        insertPublished("Rejection Co", "technical", "rejection", "onsite", "", "",
                List.of("algorithm"), "Rejection outcome mapping test note with enough text.");
        insertPublished("Ghosted Co", "technical", "ghosted", "onsite", "", "",
                List.of("algorithm"), "Ghosted outcome mapping test note with enough text.");
        insertPublished("Waiting Co", "technical", "waiting", "onsite", "", "",
                List.of("algorithm"), "Waiting outcome mapping test note with enough text.");

        List<Map<String, Object>> items = itemsOf(getExperiences());
        Map<String, String> byCompany = items.stream().collect(
                java.util.stream.Collectors.toMap(
                        item -> (String) item.get("company"),
                        item -> (String) item.get("outcome")));

        assertThat(byCompany).containsEntry("Offer Co", "OFFER")
                .containsEntry("Rejection Co", "REJECTED")
                .containsEntry("Ghosted Co", "NO_RESPONSE")
                .containsEntry("Waiting Co", "PENDING");
    }

    @Test
    void responseCarriesNoStoreNoIndexAndNoCorsHeaders() throws Exception {
        insertPublished("Headers Co");

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", bearerHeader()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", ReadApiHeaders.CACHE_CONTROL_VALUE))
                .andExpect(header().string("Pragma", ReadApiHeaders.PRAGMA_VALUE))
                .andExpect(header().string("X-Robots-Tag", ReadApiHeaders.X_ROBOTS_TAG_VALUE))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void envelopeShapeAlwaysContainsItemsNextCursorAndHasMore() throws Exception {
        insertPublished("Envelope Co");

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header("Authorization", bearerHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.has_more").isBoolean())
                .andExpect(jsonPath("$.next_cursor").doesNotExist());
    }

    @Test
    void browserHtmlAcceptHeaderStillGetsJsonOnSuccess() throws Exception {
        insertPublished("Accept Html Co");

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerHeader())
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(jsonPath("$.items[0].company").value("Accept Html Co"));
    }

    @Test
    void browserHtmlAcceptHeaderStillGetsJsonOnAuthFailure() throws Exception {
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void browserHtmlAcceptHeaderStillGetsJsonForInvalidQuery() throws Exception {
        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerHeader())
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                        .param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(jsonPath("$.error.code").value("invalid_cursor"));
    }

    @Test
    void xmlAcceptHeaderStillGetsJson() throws Exception {
        insertPublished("Accept Xml Co");

        mockMvc.perform(get(ReadApiController.EXPERIENCES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearerHeader())
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[0].company").value("Accept Xml Co"));
    }

    @Test
    void browserHtmlAcceptHeaderOnAnUnknownApiPathStillGetsJson404() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }
}
