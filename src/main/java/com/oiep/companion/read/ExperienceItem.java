package com.oiep.companion.read;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One item of the read-only experiences API ({@code interview-experience/v1}).
 *
 * <p>Two shapes share this type:</p>
 * <ul>
 *   <li><strong>Full item</strong> — a currently published report, mapped by
 *       {@link InterviewExperienceAdapter}; carries every contract field.</li>
 *   <li><strong>Tombstone</strong> — a hidden or deleted report; only {@code id},
 *       {@code updated_at} and {@code deleted:true} are emitted. The companion service
 *       removes such an id from its corpus mirror. Tombstones are created with
 *       {@link #tombstone(String, String)}.</li>
 * </ul>
 *
 * <p>Optional fields that OIEP does not collect (or that are blank) are omitted rather than
 * sent as {@code null}; {@code role_level}, {@code employment_type} and
 * {@code interview_date} are never emitted at all.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExperienceItem(
        String id,
        @JsonProperty("submitted_at") String submittedAt,
        @JsonProperty("updated_at") String updatedAt,
        boolean deleted,
        @JsonProperty("schema_version") String schemaVersion,
        String company,
        @JsonProperty("role_title") String roleTitle,
        String location,
        @JsonProperty("work_mode") String workMode,
        String outcome,
        String difficulty,
        List<ExperienceRound> rounds,
        @JsonProperty("overall_experience") String overallExperience) {

    /** Stable, < 200 character corpus id derived from the OIEP report id. */
    public static final String ID_PREFIX = "oiep-r";

    /** The only schema version this API emits. */
    public static final String SCHEMA_VERSION = "interview-experience/v1";

    /** A removal marker for a hidden or deleted report. */
    public static ExperienceItem tombstone(String id, String updatedAt) {
        return new ExperienceItem(id, null, updatedAt, true, null,
                null, null, null, null, null, null, null, null);
    }

    /**
     * True when this item is a tombstone rather than a full experience record. Convenience for
     * callers/tests only: it is not part of the {@code oiep-read-api/v1} wire contract, so
     * Jackson must not emit a {@code "tombstone"} field.
     */
    @JsonIgnore
    public boolean isTombstone() {
        return deleted;
    }
}
