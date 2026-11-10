package com.oiep.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * In-memory, per-client sliding-window rate limiter for anonymous form submissions.
 *
 * <p>Counts every POST attempt (valid or not) per client. Deliberately simple and
 * single-instance: it is abuse friction for a self-hosted community site, not a security
 * boundary. The client key comes from the trusted client-address resolver, so spoofed
 * {@code X-Forwarded-For} headers cannot dodge the limit.</p>
 */
@Component
public class SubmissionRateLimiter extends SlidingWindowRateLimiter {

    @Autowired
    public SubmissionRateLimiter(
            @Value("${oiep.rate-limit.max-requests:8}") int maxRequests,
            @Value("${oiep.rate-limit.window-seconds:600}") long windowSeconds,
            @Value("${oiep.rate-limit.max-entries:10000}") int maxEntries) {
        this(maxRequests, windowSeconds, maxEntries, Clock.systemUTC());
    }

    SubmissionRateLimiter(int maxRequests, long windowSeconds, int maxEntries, Clock clock) {
        super(maxRequests, windowSeconds, maxEntries, clock);
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        return super.tryAcquire(clientKey);
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public int recordedAttempts(String clientKey) {
        return super.recordedAttempts(clientKey);
    }
}
