package com.oiep.service;

import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks which reports the current session already marked as useful, so the helpful counter
 * can only be incremented once per browsing session (no accounts in this phase).
 */
public final class HelpfulSessionTracker {

    public static final String SESSION_ATTRIBUTE = "oiep.helpfulReportIds";

    /** Keep the session small even for long-lived sessions; oldest entries are evicted. */
    private static final int MAX_TRACKED_REPORTS = 2000;

    private HelpfulSessionTracker() {
    }

    /** Read-only check that never creates a session. */
    public static boolean isHelpful(HttpSession session, long reportId) {
        if (session == null) {
            return false;
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(value instanceof Set<?> set)) {
            return false;
        }
        synchronized (set) {
            return set.contains(reportId);
        }
    }

    /**
     * Marks the report as helpful for this session.
     *
     * @return {@code true} the first time (caller should increment the counter),
     *         {@code false} when it was already marked
     */
    public static boolean markHelpful(HttpSession session, long reportId) {
        Set<Long> tracked = getOrCreate(session);
        synchronized (tracked) {
            if (!tracked.add(reportId)) {
                return false;
            }
            while (tracked.size() > MAX_TRACKED_REPORTS) {
                Iterator<Long> iterator = tracked.iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Long> getOrCreate(HttpSession session) {
        synchronized (session) {
            Object value = session.getAttribute(SESSION_ATTRIBUTE);
            if (value instanceof Set<?> existing) {
                return (Set<Long>) existing;
            }
            Set<Long> created = Collections.synchronizedSet(new LinkedHashSet<>());
            session.setAttribute(SESSION_ATTRIBUTE, created);
            return created;
        }
    }
}
