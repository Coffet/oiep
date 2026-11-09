package com.oiep.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single source of truth for the enums accepted by the public form and the browse filters,
 * plus the server-side normalization/sanitization helpers shared by controllers and the
 * repository.
 *
 * <p>The regex constants are used by Bean Validation annotations, so they must stay
 * compile-time constants.</p>
 */
public final class ReportOptions {

    public static final List<String> ROUNDS =
            List.of("phone", "technical", "takehome", "systemdesign", "behavioral", "final", "other");
    public static final List<String> WORK_MODES = List.of("onsite", "hybrid", "remote");
    public static final List<String> OUTCOMES = List.of("offer", "rejection", "ghosted", "waiting", "withdrew");
    public static final List<String> HEARD_BACK = List.of("days", "week", "weeks", "month", "never", "stillwaiting");
    public static final List<String> QUESTION_TYPES =
            List.of("algorithm", "systemdesign", "takehome", "behavioral", "domain", "culture", "casestudy");
    public static final List<String> VIBES = List.of("warm", "neutral", "cold", "rushed", "unclear");

    /** Kept as a literal so it can be referenced from {@code @Size(max = ...)}. */
    public static final int MAX_QUESTION_TYPES = 7;

    public static final String ROUND_ALLOWED_REGEX =
            "^(phone|technical|takehome|systemdesign|behavioral|final|other)?$";
    public static final String WORK_MODE_ALLOWED_REGEX = "^(onsite|hybrid|remote)?$";
    public static final String OUTCOME_ALLOWED_REGEX = "^(offer|rejection|ghosted|waiting|withdrew)?$";
    public static final String HEARD_BACK_ALLOWED_REGEX = "^(days|week|weeks|month|never|stillwaiting)?$";
    public static final String VIBE_ALLOWED_REGEX = "^(warm|neutral|cold|rushed|unclear)?$";
    public static final String QUESTION_TYPE_ALLOWED_REGEX =
            "^(algorithm|systemdesign|takehome|behavioral|domain|culture|casestudy)$";

    public static final String SORT_RECENT = "recent";
    public static final String SORT_HELPFUL = "helpful";

    public static final int MAX_SEARCH_LENGTH = 120;

    private ReportOptions() {
    }

    /**
     * Returns the lower-cased value when it is one of the allowed values; otherwise an empty
     * string (unknown filter values are silently ignored, never echoed back or used in SQL).
     */
    public static String normalizeFilter(String rawValue, List<String> allowedValues) {
        if (rawValue == null) {
            return "";
        }
        String candidate = rawValue.trim().toLowerCase(Locale.ROOT);
        return allowedValues.contains(candidate) ? candidate : "";
    }

    /** Normalizes a required single-value choice (empty when absent/unknown). */
    public static String normalizeChoice(String rawValue, List<String> allowedValues) {
        return normalizeFilter(rawValue, allowedValues);
    }

    /** Normalizes the browse sort key; anything unknown falls back to "recent". */
    public static String normalizeSort(String rawValue) {
        if (rawValue == null) {
            return SORT_RECENT;
        }
        return SORT_HELPFUL.equalsIgnoreCase(rawValue.trim()) ? SORT_HELPFUL : SORT_RECENT;
    }

    /**
     * Whitespace-normalizes, strips control characters and caps the free-text search term.
     */
    public static String normalizeSearch(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String cleaned = rawValue.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_SEARCH_LENGTH) {
            cleaned = cleaned.substring(0, MAX_SEARCH_LENGTH).trim();
        }
        return cleaned;
    }

    /**
     * Drops unknown, blank and duplicate question-type values and caps the list size.
     * Used after form binding and again when persisting, so even a crafted request can only
     * ever store known tags.
     */
    public static List<String> sanitizeQuestionTypes(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null) {
                continue;
            }
            String candidate = rawValue.trim().toLowerCase(Locale.ROOT);
            if (QUESTION_TYPES.contains(candidate)) {
                unique.add(candidate);
            }
            if (unique.size() >= MAX_QUESTION_TYPES) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    /** Trims user supplied single-line text; {@code null} becomes an empty string. */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
