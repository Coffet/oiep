package com.oiep.companion.read;

import com.oiep.companion.CompanionConditions;
import com.oiep.service.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Per-client throttle for the public read API ({@code GET /api/v1/experiences}).
 *
 * <p>The endpoint accepts a bearer token and has no session, so without a throttle an
 * unauthenticated caller could hammer it with guessed tokens (brute-force friction) or with
 * plain 401 traffic. This limiter counts every request for the resolved client IP, including
 * failed authentication attempts, and returns {@code 429 rate_limited} with {@code Retry-After}
 * once the window is exhausted. Succeeded requests also count: the legitimate consumer is the
 * companion's corpus sync (one page per request), which stays far below the default.</p>
 *
 * <p>The bucket map is bounded by the inherited implementation, so rotating client keys cannot
 * grow it without bound.</p>
 */
@Component
@Conditional(CompanionConditions.ReadApi.class)
public class ReadApiRateLimiter extends SlidingWindowRateLimiter {

    /** Default window size for the read API bucket. */
    public static final int DEFAULT_MAX_REQUESTS = 120;
    public static final long DEFAULT_WINDOW_SECONDS = 60L;
    public static final int DEFAULT_MAX_TRACKED_CLIENTS = 10_000;

    public ReadApiRateLimiter(
            @Value("${oiep.companion.read-api.rate-limit.max-requests:"
                    + DEFAULT_MAX_REQUESTS + "}") int maxRequests,
            @Value("${oiep.companion.read-api.rate-limit.window-seconds:"
                    + DEFAULT_WINDOW_SECONDS + "}") long windowSeconds,
            @Value("${oiep.companion.read-api.rate-limit.max-tracked-clients:"
                    + DEFAULT_MAX_TRACKED_CLIENTS + "}") int maxTrackedClients) {
        super(maxRequests, windowSeconds, maxTrackedClients, Clock.systemUTC());
    }

    /** Records one request for the client; {@code false} means the bucket is exhausted. */
    public boolean allow(String clientKey) {
        return tryAcquire(clientKey);
    }

    /** Seconds a throttled caller should wait before retrying (the window length). */
    public long retryAfterSeconds() {
        return DEFAULT_WINDOW_SECONDS;
    }

    /** Test/support hook: forget every client bucket. */
    @Override
    public void reset() {
        super.reset();
    }
}
