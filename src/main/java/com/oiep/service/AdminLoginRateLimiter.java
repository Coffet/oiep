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
 * Small in-memory login throttle for the admin area. Counts attempts per client address and
 * deliberately fails closed once the configured limit is reached. This is friction against
 * password guessing, not a replacement for a reverse-proxy/WAF layer.
 */
@Component
public class AdminLoginRateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final int maxEntries;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public AdminLoginRateLimiter(
            @Value("${oiep.admin.login.max-attempts:6}") int maxAttempts,
            @Value("${oiep.admin.login.window-seconds:900}") long windowSeconds,
            @Value("${oiep.admin.login.max-entries:10000}") int maxEntries) {
        this(maxAttempts, windowSeconds, maxEntries, Clock.systemUTC());
    }

    AdminLoginRateLimiter(int maxAttempts, long windowSeconds, int maxEntries, Clock clock) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMillis = Math.max(1L, windowSeconds) * 1000L;
        this.maxEntries = Math.max(16, maxEntries);
        this.clock = clock;
    }

    public boolean tryAcquire(String clientKey) {
        String bucket = clientKey == null || clientKey.isBlank() ? "unknown" : clientKey;
        long now = clock.millis();
        long cutoff = now - windowMillis;

        Deque<Long> timestamps = attempts.computeIfAbsent(bucket, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                return false;
            }
            timestamps.addLast(now);
        }

        if (attempts.size() > maxEntries) {
            evictStaleEntries(cutoff);
        }
        return true;
    }

    public void reset() {
        attempts.clear();
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
