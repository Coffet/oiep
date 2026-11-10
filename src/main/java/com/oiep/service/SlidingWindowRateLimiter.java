package com.oiep.service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small in-memory, per-client sliding-window rate limiter.
 *
 * <p>Shared by every per-client throttle in the application (anonymous submissions, useful
 * votes, admin login attempts and login-page requests). The map of client buckets is bounded:
 * stale buckets are pruned and, when an attacker presents a large number of distinct client
 * keys, the oldest buckets are dropped once the hard cap is exceeded. That keeps the limiter
 * from becoming a memory-exhaustion vector itself.</p>
 */
public abstract class SlidingWindowRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final int maxEntries;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    protected SlidingWindowRateLimiter(int maxRequests, long windowSeconds, int maxEntries, Clock clock) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMillis = Math.max(1L, windowSeconds) * 1000L;
        this.maxEntries = Math.max(16, maxEntries);
        this.clock = clock;
    }

    /**
     * Records one attempt for the given client key and returns {@code false} when the client
     * has used up the allowed attempts inside the current window.
     */
    protected boolean tryAcquire(String clientKey) {
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
            evictOverflow(cutoff);
        }
        return allowed;
    }

    /** Test/support hook: forget all buckets. */
    protected void reset() {
        attempts.clear();
    }

    /** Number of attempts currently recorded for a key (test/support hook). */
    int recordedAttempts(String clientKey) {
        Deque<Long> timestamps = attempts.get(clientKey == null ? "unknown" : clientKey);
        if (timestamps == null) {
            return 0;
        }
        synchronized (timestamps) {
            return timestamps.size();
        }
    }

    /** Number of client buckets currently tracked (test/support hook). */
    int trackedBuckets() {
        return attempts.size();
    }

    private void evictOverflow(long cutoff) {
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
        if (attempts.size() <= maxEntries) {
            return;
        }
        // Hard cap. A client that rotates keys (spoofed proxy headers, IPv6 rotation, or a
        // botnet) must not be able to grow this map without bound. Evict down to 90% of the
        // cap so the O(n) sweep is amortised over many new buckets instead of every request.
        int targetSize = Math.max(16, (maxEntries * 9) / 10);

        // Prefer evicting buckets that still have budget. Evicting a bucket that is currently
        // over its limit would hand that client a fresh allowance, so blocked buckets are kept
        // while any non-blocked bucket remains.
        int toRemove = attempts.size() - targetSize;
        Iterator<Map.Entry<String, Deque<Long>>> iterator = attempts.entrySet().iterator();
        while (toRemove > 0 && iterator.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = iterator.next();
            if (bucketSize(entry.getValue()) >= maxRequests) {
                continue;
            }
            iterator.remove();
            toRemove--;
        }

        // Emergency bound: if the map is more than twice the configured cap (a client
        // deliberately filled it with blocked buckets), enforce the ceiling anyway. Dropping a
        // blocked bucket can reset one allowance, which is preferable to unbounded memory.
        if (attempts.size() <= maxEntries * 2L) {
            return;
        }
        int emergencyRemove = attempts.size() - targetSize;
        Iterator<Map.Entry<String, Deque<Long>>> emergencyIterator = attempts.entrySet().iterator();
        while (emergencyRemove > 0 && emergencyIterator.hasNext()) {
            emergencyIterator.next();
            emergencyIterator.remove();
            emergencyRemove--;
        }
    }

    private static int bucketSize(Deque<Long> timestamps) {
        synchronized (timestamps) {
            return timestamps.size();
        }
    }
}
