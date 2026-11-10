package com.oiep.companion.read;

import com.oiep.companion.CompanionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single OIEP → {@code interview-experience/v1} adapter used by the read-only experiences
 * API.
 *
 * <p>OIEP stores one note per submission (company, role, round, work mode, location, outcome,
 * question-type tags, free text), not a full multi-round interview loop. This adapter is the
 * one place where that shape is translated into the companion's data contract:</p>
 *
 * <table>
 *   <caption>Field mapping</caption>
 *   <tr><th>OIEP</th><th>interview-experience/v1</th></tr>
 *   <tr><td>{@code "oiep-r" + id}</td><td>{@code id}</td></tr>
 *   <tr><td>{@code created_at}</td><td>{@code submitted_at}</td></tr>
 *   <tr><td>component event time</td><td>{@code updated_at}</td></tr>
 *   <tr><td>{@code status != published}</td><td>{@code deleted: true} (tombstone)</td></tr>
 *   <tr><td>{@code company}</td><td>{@code company}</td></tr>
 *   <tr><td>{@code role}</td><td>{@code role_title}</td></tr>
 *   <tr><td>{@code location} (omitted when blank)</td><td>{@code location}</td></tr>
 *   <tr><td>{@code work_mode} onsite/hybrid/remote (omitted when blank)</td>
 *       <td>{@code work_mode} ONSITE/HYBRID/REMOTE</td></tr>
 *   <tr><td>{@code outcome} offer/rejection/ghosted/waiting/withdrew</td>
 *       <td>{@code outcome} OFFER/REJECTED/NO_RESPONSE/PENDING/WITHDRAWN</td></tr>
 *   <tr><td>{@code round} phone/technical/takehome/systemdesign/behavioral/final/other</td>
 *       <td>one round entry: PHONE/TECHNICAL/TAKE_HOME/SYSTEM_DESIGN/BEHAVIORAL/ONSITE/OTHER</td></tr>
 *   <tr><td>{@code question_types}</td><td>round {@code topics} (max 8)</td></tr>
 *   <tr><td>{@code vibe} (only in {@code vibe-proxy} difficulty mode)</td><td>{@code difficulty}</td></tr>
 *   <tr><td>{@code story}</td><td>{@code overall_experience}</td></tr>
 * </table>
 *
 * <p><strong>Difficulty is not collected by the OIEP form.</strong> The default
 * {@code constant} mode therefore emits one documented configured value (default
 * {@code MODERATE}); the optional {@code vibe-proxy} mode derives a coarse, clearly approximate
 * value from the interviewer-vibe field. Analytics that group by difficulty are approximate by
 * construction.</p>
 *
 * <p>Free text is whitespace-normalised and stripped of control characters here: the companion
 * mirror validator rejects control characters in its text columns, while an OIEP textarea
 * legitimately contains newlines. Sizes are capped at the contract limits so every published
 * report produces a mirror-acceptable item.</p>
 */
public class InterviewExperienceAdapter {

    private static final Logger log = LoggerFactory.getLogger(InterviewExperienceAdapter.class);

    /** The optional difficulty mode that derives a value from the interviewer-vibe answer. */
    public static final String DIFFICULTY_MODE_VIBE_PROXY = "vibe-proxy";

    /** Fallback used for a missing/unknown configured constant difficulty. */
    public static final String DEFAULT_DIFFICULTY = "MODERATE";

    private static final Set<String> DIFFICULTIES = Set.of(
            "VERY_EASY", "EASY", "MODERATE", "HARD", "VERY_HARD");

    private static final Map<String, String> WORK_MODES = Map.of(
            "onsite", "ONSITE",
            "hybrid", "HYBRID",
            "remote", "REMOTE");

    private static final Map<String, String> OUTCOMES = Map.of(
            "offer", "OFFER",
            "rejection", "REJECTED",
            "ghosted", "NO_RESPONSE",
            "waiting", "PENDING",
            "withdrew", "WITHDRAWN");

    private static final Map<String, String> ROUND_TYPES = Map.of(
            "phone", "PHONE",
            "technical", "TECHNICAL",
            "takehome", "TAKE_HOME",
            "systemdesign", "SYSTEM_DESIGN",
            "behavioral", "BEHAVIORAL",
            "final", "ONSITE",
            "other", "OTHER");

    /** Coarse, documented difficulty proxy from the OIEP interviewer-vibe answer. */
    private static final Map<String, String> VIBE_DIFFICULTIES = Map.of(
            "warm", "EASY",
            "neutral", "MODERATE",
            "cold", "HARD",
            "rushed", "HARD",
            "unclear", "MODERATE");

    private static final int MAX_COMPANY_LENGTH = 120;
    private static final int MAX_ROLE_TITLE_LENGTH = 160;
    private static final int MAX_LOCATION_LENGTH = 120;
    private static final int MAX_OVERALL_EXPERIENCE_LENGTH = 5000;
    private static final int MAX_TOPICS = 8;
    private static final int MAX_TOPIC_LENGTH = 60;

    private final String constantDifficulty;
    private final boolean vibeProxy;

    /**
     * @param difficultyMode {@code constant} (default) or {@code vibe-proxy}
     * @param difficulty     the value emitted in constant mode; unknown values fall back to
     *                       {@link #DEFAULT_DIFFICULTY}
     */
    public InterviewExperienceAdapter(String difficultyMode, String difficulty) {
        this.vibeProxy = DIFFICULTY_MODE_VIBE_PROXY.equalsIgnoreCase(trimToEmpty(difficultyMode));
        this.constantDifficulty = normaliseDifficulty(difficulty);
    }

    /** Adapter configured from the component properties. */
    public InterviewExperienceAdapter(CompanionProperties properties) {
        this(properties.getReadApi().getDifficultyMode(), properties.getReadApi().getDifficulty());
    }

    /** Maps one event/report row to a full item or a deletion tombstone. */
    public ExperienceItem adapt(ReadApiRow row) {
        String id = ExperienceItem.ID_PREFIX + row.reportId();
        String updatedAt = ReadApiTimestamps.isoFromEpochMillis(row.eventMs());
        if (row.eventDeleted() || !row.reportPresent() || !"published".equals(row.status())) {
            return ExperienceItem.tombstone(id, updatedAt);
        }
        String submittedAt = ReadApiTimestamps.isoOr(row.createdAt(), updatedAt);
        String company = normaliseText(row.company(), MAX_COMPANY_LENGTH);
        String roleTitle = normaliseText(row.role(), MAX_ROLE_TITLE_LENGTH);
        String location = normaliseText(row.location(), MAX_LOCATION_LENGTH);
        String overallExperience = normaliseText(row.story(), MAX_OVERALL_EXPERIENCE_LENGTH);
        List<String> topics = parseTopics(row.questionTypes());
        ExperienceRound round = new ExperienceRound(
                1,
                ROUND_TYPES.getOrDefault(trimToEmpty(row.round()).toLowerCase(Locale.ROOT), "OTHER"),
                "UNKNOWN",
                topics.isEmpty() ? null : topics,
                null);
        return new ExperienceItem(
                id,
                submittedAt,
                updatedAt,
                false,
                ExperienceItem.SCHEMA_VERSION,
                company,
                roleTitle,
                location.isEmpty() ? null : location,
                WORK_MODES.get(trimToEmpty(row.workMode()).toLowerCase(Locale.ROOT)),
                OUTCOMES.getOrDefault(trimToEmpty(row.outcome()).toLowerCase(Locale.ROOT), "PENDING"),
                difficulty(row.vibe()),
                List.of(round),
                overallExperience);
    }

    /** The configured constant difficulty (also the value used when vibe-proxy finds no vibe). */
    public String constantDifficulty() {
        return constantDifficulty;
    }

    /** True when this adapter derives difficulty from the interviewer-vibe answer. */
    public boolean isVibeProxy() {
        return vibeProxy;
    }

    private String difficulty(String rawVibe) {
        if (!vibeProxy) {
            return constantDifficulty;
        }
        return VIBE_DIFFICULTIES.getOrDefault(
                trimToEmpty(rawVibe).toLowerCase(Locale.ROOT), DEFAULT_DIFFICULTY);
    }

    /**
     * Whitespace-normalises and strips control characters, then caps the result at the contract
     * maximum. Blank values become {@code ""}.
     */
    static String normaliseText(String rawValue, int maxLength) {
        String candidate = trimToEmpty(rawValue);
        if (candidate.isEmpty()) {
            return "";
        }
        String cleaned = candidate.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength).trim();
        }
        return cleaned;
    }

    /** Splits the stored comma-separated question types into at most {@value #MAX_TOPICS} topics. */
    static List<String> parseTopics(String rawQuestionTypes) {
        String candidate = trimToEmpty(rawQuestionTypes);
        if (candidate.isEmpty()) {
            return List.of();
        }
        Set<String> topics = new LinkedHashSet<>();
        for (String raw : candidate.split(",")) {
            String topic = normaliseText(raw, MAX_TOPIC_LENGTH);
            if (!topic.isEmpty()) {
                topics.add(topic);
            }
            if (topics.size() >= MAX_TOPICS) {
                break;
            }
        }
        return List.copyOf(topics);
    }

    private static String normaliseDifficulty(String rawDifficulty) {
        String candidate = trimToEmpty(rawDifficulty).toUpperCase(Locale.ROOT);
        if (DIFFICULTIES.contains(candidate)) {
            return candidate;
        }
        if (!candidate.isEmpty()) {
            log.warn("AI companion read API difficulty '{}' is not a contract value; using {}",
                    candidate, DEFAULT_DIFFICULTY);
        }
        return DEFAULT_DIFFICULTY;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
