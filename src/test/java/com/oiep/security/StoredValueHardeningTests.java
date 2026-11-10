package com.oiep.security;

import com.oiep.IntegrationTestSupport;
import com.oiep.domain.Report;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The database file is an external boundary (restore, direct edit, import). Allowed-value
 * columns are re-validated when read so a hand-crafted row can never break message lookup or
 * inject message-code fragments into the JSPs.
 */
class StoredValueHardeningTests extends IntegrationTestSupport {

    @Test
    void craftedEnumColumnsAreSanitisedOnRead() throws Exception {
        long id = insertCraftedRow("<script>alert(1)</script>", "<b>", "<i>", "<script>,algorithm,<b>", "<u>");

        Report report = reportRepository.findPublishedById(id).orElseThrow();
        assertThat(report.getRound()).isEqualTo("other");
        assertThat(report.getOutcome()).isEqualTo("waiting");
        assertThat(report.getWorkMode()).isEmpty();
        assertThat(report.getHeardBack()).isEmpty();
        assertThat(report.getVibe()).isEmpty();
        assertThat(report.getQuestionTypes()).containsExactly("algorithm");

        mockMvc.perform(get("/reports/" + id))
                .andExpect(status().isOk())
                .andExpect(view().name("report"));
        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void recognizedValuesAreCanonicalisedOnRead() throws Exception {
        long id = insertCraftedRow("TECHNICAL", "hybrid", "week", "ALGORITHM, systemdesign", "WARM");

        Report report = reportRepository.findById(id).orElseThrow();
        assertThat(report.getRound()).isEqualTo("technical");
        assertThat(report.getOutcome()).isEqualTo("waiting"); // crafted outcome stays invalid -> safe fallback
        assertThat(report.getWorkMode()).isEqualTo("hybrid");
        assertThat(report.getHeardBack()).isEqualTo("week");
        assertThat(report.getVibe()).isEqualTo("warm");
        assertThat(report.getQuestionTypes()).containsExactly("algorithm", "systemdesign");
    }

    private long insertCraftedRow(String round, String workMode, String heardBack, String questionTypes, String vibe) {
        jdbcTemplate.update("INSERT INTO reports (company, role, round, work_mode, location, outcome, "
                        + "heard_back, question_types, salary, vibe, story, helpful, created_at, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "Crafted Co", "Engineer", round, workMode, "Hong Kong", "<script>",
                heardBack, questionTypes, "<b>", vibe,
                "A sufficiently long story for the crafted stored-value hardening test.",
                0, Instant.now().toString(), "published");
        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        assertThat(id).isNotNull();
        return id;
    }
}
