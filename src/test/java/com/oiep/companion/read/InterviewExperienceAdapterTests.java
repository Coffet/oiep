package com.oiep.companion.read;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the single OIEP → {@code interview-experience/v1} adapter: the mapping
 * table, the documented difficulty adapter (constant + optional vibe proxy), the fixed
 * single-round shape, blank-field omission and defensive text normalisation.
 */
class InterviewExperienceAdapterTests {

    private static final long EVENT_MS = 1_762_765_445_123L;
    private static final String CREATED_AT = "2026-11-09T14:43:12Z";
    private static final String STORY =
            "Three rounds, clear feedback, and the questions matched the job description.";

    private final InterviewExperienceAdapter constantAdapter =
            new InterviewExperienceAdapter("constant", "MODERATE");

    @Test
    void mapsEveryContractFieldForAPublishedReport() {
        ExperienceItem item = constantAdapter.adapt(publishedRow(42L));

        assertThat(item.id()).isEqualTo("oiep-r42");
        assertThat(item.submittedAt()).isEqualTo(CREATED_AT);
        assertThat(item.updatedAt()).isEqualTo(Instant.ofEpochMilli(EVENT_MS).toString());
        assertThat(item.deleted()).isFalse();
        assertThat(item.schemaVersion()).isEqualTo("interview-experience/v1");
        assertThat(item.company()).isEqualTo("Banyan Stack");
        assertThat(item.roleTitle()).isEqualTo("Backend Engineer");
        assertThat(item.location()).isEqualTo("Hong Kong");
        assertThat(item.workMode()).isEqualTo("HYBRID");
        assertThat(item.outcome()).isEqualTo("OFFER");
        assertThat(item.difficulty()).isEqualTo("MODERATE");
        assertThat(item.overallExperience()).isEqualTo(STORY);
        assertThat(item.rounds()).singleElement().satisfies(round -> {
            assertThat(round.index()).isEqualTo(1);
            assertThat(round.type()).isEqualTo("TECHNICAL");
            assertThat(round.format()).isEqualTo("UNKNOWN");
            assertThat(round.topics()).containsExactly("algorithm", "behavioral");
            assertThat(round.notes()).isNull();
        });
    }

    @Test
    void mapsEveryWorkMode() {
        assertThat(adaptedWorkMode("onsite")).isEqualTo("ONSITE");
        assertThat(adaptedWorkMode("hybrid")).isEqualTo("HYBRID");
        assertThat(adaptedWorkMode("remote")).isEqualTo("REMOTE");
        assertThat(adaptedWorkMode("")).isNull();
        assertThat(adaptedWorkMode("on-site")).isNull();
    }

    @Test
    void mapsEveryOutcome() {
        assertThat(adaptedOutcome("offer")).isEqualTo("OFFER");
        assertThat(adaptedOutcome("rejection")).isEqualTo("REJECTED");
        assertThat(adaptedOutcome("ghosted")).isEqualTo("NO_RESPONSE");
        assertThat(adaptedOutcome("waiting")).isEqualTo("PENDING");
        assertThat(adaptedOutcome("withdrew")).isEqualTo("WITHDRAWN");
        // Outcome is required by the companion store: unknown values must still be emitted.
        assertThat(adaptedOutcome("")).isEqualTo("PENDING");
        assertThat(adaptedOutcome("not-a-choice")).isEqualTo("PENDING");
    }

    @Test
    void mapsEveryRoundTypeToASingleRound() {
        assertThat(adaptedRoundType("phone")).isEqualTo("PHONE");
        assertThat(adaptedRoundType("technical")).isEqualTo("TECHNICAL");
        assertThat(adaptedRoundType("takehome")).isEqualTo("TAKE_HOME");
        assertThat(adaptedRoundType("systemdesign")).isEqualTo("SYSTEM_DESIGN");
        assertThat(adaptedRoundType("behavioral")).isEqualTo("BEHAVIORAL");
        assertThat(adaptedRoundType("final")).isEqualTo("ONSITE");
        assertThat(adaptedRoundType("other")).isEqualTo("OTHER");
        assertThat(adaptedRoundType("")).isEqualTo("OTHER");
    }

    @Test
    void constantModeUsesConfiguredDifficultyAndFallsBackForUnknownValues() {
        assertThat(new InterviewExperienceAdapter("constant", "HARD").constantDifficulty())
                .isEqualTo("HARD");
        assertThat(new InterviewExperienceAdapter("constant", "hard").constantDifficulty())
                .isEqualTo("HARD");
        assertThat(new InterviewExperienceAdapter("constant", "EXTREME").constantDifficulty())
                .isEqualTo("MODERATE");
        assertThat(new InterviewExperienceAdapter("", "").constantDifficulty())
                .isEqualTo("MODERATE");
        assertThat(new InterviewExperienceAdapter("constant", "HARD").isVibeProxy()).isFalse();
    }

    @Test
    void vibeProxyModeMapsVibeDeterministically() {
        InterviewExperienceAdapter proxy =
                new InterviewExperienceAdapter("vibe-proxy", "MODERATE");

        assertThat(proxy.isVibeProxy()).isTrue();
        assertThat(adaptedDifficulty(proxy, "warm")).isEqualTo("EASY");
        assertThat(adaptedDifficulty(proxy, "neutral")).isEqualTo("MODERATE");
        assertThat(adaptedDifficulty(proxy, "cold")).isEqualTo("HARD");
        assertThat(adaptedDifficulty(proxy, "rushed")).isEqualTo("HARD");
        assertThat(adaptedDifficulty(proxy, "unclear")).isEqualTo("MODERATE");
        assertThat(adaptedDifficulty(proxy, "")).isEqualTo("MODERATE");
        assertThat(adaptedDifficulty(proxy, "unknown-value")).isEqualTo("MODERATE");
    }

    @Test
    void hiddenDeletedOrVanishedReportsBecomeTombstones() {
        ExperienceItem hidden = constantAdapter.adapt(
                row(7L).status("hidden").build());
        assertThat(hidden.id()).isEqualTo("oiep-r7");
        assertThat(hidden.deleted()).isTrue();
        assertThat(hidden.isTombstone()).isTrue();
        assertThat(hidden.updatedAt()).isEqualTo(Instant.ofEpochMilli(EVENT_MS).toString());
        assertThat(hidden.company()).isNull();
        assertThat(hidden.overallExperience()).isNull();
        assertThat(hidden.schemaVersion()).isNull();

        ExperienceItem deletedEvent = constantAdapter.adapt(row(8L).eventDeleted(true).build());
        assertThat(deletedEvent.deleted()).isTrue();

        ExperienceItem vanished = constantAdapter.adapt(
                row(9L).reportPresent(false).status(null).build());
        assertThat(vanished.deleted()).isTrue();
        assertThat(vanished.id()).isEqualTo("oiep-r9");
    }

    @Test
    void normalisesFreeTextSoTheCompanionMirrorAcceptsIt() {
        ExperienceItem item = constantAdapter.adapt(row(11L)
                .story("Line one.\n\tLine two.\r\nLine three.\u0007")
                .company("  Banyan\nStack  ")
                .location(" Hong\tKong ")
                .build());

        assertThat(item.overallExperience()).isEqualTo("Line one. Line two. Line three.");
        assertThat(item.company()).isEqualTo("Banyan Stack");
        assertThat(item.location()).isEqualTo("Hong Kong");
    }

    @Test
    void capsOverallExperienceAtTheContractLimit() {
        String longStory = "word ".repeat(2000);
        ExperienceItem item = constantAdapter.adapt(row(12L).story(longStory).build());

        assertThat(item.overallExperience().length()).isLessThanOrEqualTo(5000);
        assertThat(item.overallExperience()).startsWith("word word");
    }

    @Test
    void questionsBecomeTopicsCappedAtEight() {
        ExperienceItem item = constantAdapter.adapt(row(13L)
                .questionTypes("algorithm, systemdesign,takehome,behavioral,domain,culture,"
                        + "casestudy,algorithm,extra-ninth,extra-tenth")
                .build());

        assertThat(item.rounds()).singleElement().satisfies(round ->
                assertThat(round.topics()).containsExactly(
                        "algorithm", "systemdesign", "takehome", "behavioral",
                        "domain", "culture", "casestudy", "extra-ninth"));
    }

    @Test
    void blankTopicsAreOmitted() {
        ExperienceItem item = constantAdapter.adapt(row(14L).questionTypes(" , ").build());

        assertThat(item.rounds()).singleElement().satisfies(round ->
                assertThat(round.topics()).isNull());
    }

    @Test
    void unparsableCreatedAtFallsBackToTheEventTime() {
        ExperienceItem item = constantAdapter.adapt(row(15L)
                .createdAt("not-a-timestamp")
                .build());

        assertThat(item.submittedAt()).isEqualTo(Instant.ofEpochMilli(EVENT_MS).toString());
        assertThat(item.updatedAt()).isEqualTo(item.submittedAt());
    }

    private String adaptedWorkMode(String workMode) {
        return constantAdapter.adapt(row(1L).workMode(workMode).build()).workMode();
    }

    private String adaptedOutcome(String outcome) {
        return constantAdapter.adapt(row(1L).outcome(outcome).build()).outcome();
    }

    private String adaptedRoundType(String round) {
        ExperienceItem item = constantAdapter.adapt(row(1L).round(round).build());
        return item.rounds().get(0).type();
    }

    private static String adaptedDifficulty(InterviewExperienceAdapter adapter, String vibe) {
        return adapter.adapt(row(1L).vibe(vibe).build()).difficulty();
    }

    private static ReadApiRow publishedRow(long reportId) {
        return row(reportId).build();
    }

    private static RowBuilder row(long reportId) {
        return new RowBuilder(reportId);
    }

    /** Small mutable builder so each test only overrides the field it exercises. */
    private static final class RowBuilder {

        private final long reportId;
        private long eventMs = EVENT_MS;
        private boolean eventDeleted;
        private boolean reportPresent = true;
        private String company = "Banyan Stack";
        private String role = "Backend Engineer";
        private String round = "technical";
        private String workMode = "hybrid";
        private String location = "Hong Kong";
        private String outcome = "offer";
        private String questionTypes = "algorithm,behavioral";
        private String vibe = "warm";
        private String story = STORY;
        private String createdAt = CREATED_AT;
        private String status = "published";

        private RowBuilder(long reportId) {
            this.reportId = reportId;
        }

        private RowBuilder eventDeleted(boolean value) {
            this.eventDeleted = value;
            return this;
        }

        private RowBuilder reportPresent(boolean value) {
            this.reportPresent = value;
            return this;
        }

        private RowBuilder company(String value) {
            this.company = value;
            return this;
        }

        private RowBuilder round(String value) {
            this.round = value;
            return this;
        }

        private RowBuilder workMode(String value) {
            this.workMode = value;
            return this;
        }

        private RowBuilder location(String value) {
            this.location = value;
            return this;
        }

        private RowBuilder outcome(String value) {
            this.outcome = value;
            return this;
        }

        private RowBuilder questionTypes(String value) {
            this.questionTypes = value;
            return this;
        }

        private RowBuilder vibe(String value) {
            this.vibe = value;
            return this;
        }

        private RowBuilder story(String value) {
            this.story = value;
            return this;
        }

        private RowBuilder createdAt(String value) {
            this.createdAt = value;
            return this;
        }

        private RowBuilder status(String value) {
            this.status = value;
            return this;
        }

        private ReadApiRow build() {
            return new ReadApiRow(reportId, eventMs, eventDeleted, reportPresent, company, role,
                    round, workMode, location, outcome, questionTypes, vibe, story, createdAt,
                    status);
        }
    }
}
