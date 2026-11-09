package com.oiep.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small in-memory, per-client sliding-window rate limiter for form submissions.
 *
 * <p>Counts every POST attempt (valid or not) per remote address. Deliberately simple and
 * single-instance: it is abuse friction for a self-hosted community site, not a security
 * boundary. Behind a reverse proxy all clients share the proxy address unless the container is
 * configured for forwarded headers, so the limit is intentionally generous.</p>
 */
@Component
public class SubmissionRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final int maxEntries;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public SubmissionRateLimiter(
            @Value("${oiep.rate-limit.max-requests:8}") int maxRequests,
            @Value("${oiep.rate-limit.window-seconds:600}") long windowSeconds,
            @Value("${oiep.rate-limit.max-entries:10000}") int maxEntries) {
        this(maxRequests, windowSeconds, maxEntries, Clock.systemUTC());
    }

    SubmissionRateLimiter(int maxRequests, long windowSeconds, int maxEntries, Clock clock) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMillis = Math.max(1L, windowSeconds) * 1000L;
        this.maxEntries = Math.max(16, maxEntries);
        this.clock = clock;
    }

    /**
     * Records one attempt for the given client key and returns {@code false} when the client
     * has used up the allowed attempts inside the current window.
     */
    public boolean tryAcquire(String clientKey) {
        String bucket = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey;
        long now = clock.millis();
        long cutoff = now - windowMillis;

        Deque<Long> timestamps = attempts.computeIfAbsent(bucket, ignored -> new ArrayDeque<>());
        boolean allowed;
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                allowed = false;
            } else {
                timestamps.addLast(now);
                allowed = true;
            }
        }

        if (attempts.size() > maxEntries) {
            evictStaleEntries(cutoff);
        }
        return allowed;
    }

    /** Test/support hook: forget all buckets. */
    public void reset() {
        attempts.clear();
    }

    /** Number of attempts currently recorded for a key (test/support hook). */
    public int recordedAttempts(String clientKey) {
        Deque<Long> timestamps = attempts.get(clientKey);
        if (timestamps == null) {
            return 0;
        }
        synchronized (timestamps) {
            return timestamps.size();
        }
    }

    private void evictStaleEntries(long cutoff) {
        for (Map.Entry<String, Deque<Long>> entry : attempts.entrySet()) {
            Deque<Long> timestamps = entry.getValue();
            boolean empty;
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                    timestamps.pollFirst();
                }
                empty = timestamps.isEmpty();
            }
            if (empty) {
                attempts.remove(entry.getKey(), timestamps);
            }
        }
    }
}
