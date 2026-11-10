package com.oiep.web;

/**
 * Small helper for values that originate from an HTTP request and end up in a log line.
 *
 * <p>Attackers control header and form values, including embedded newlines; logging them raw
 * would let a client forge log entries or flood the log with oversized lines. Values are
 * stripped of control characters and truncated before logging.</p>
 */
public final class LogValues {

    private static final int DEFAULT_MAX_LENGTH = 120;

    private LogValues() {
    }

    public static String safe(String value) {
        return safe(value, DEFAULT_MAX_LENGTH);
    }

    public static String safe(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int limit = Math.max(1, maxLength);
        String cleaned = value.replaceAll("\\p{Cntrl}", "?");
        return cleaned.length() <= limit ? cleaned : cleaned.substring(0, limit) + "…";
    }
}
