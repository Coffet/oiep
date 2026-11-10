package com.oiep.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The limiter map is itself an attack surface: a client that can present many distinct keys
 * (spoofed proxy headers, IPv6 rotation, a botnet) must not be able to grow it without bound.
 */
class RateLimiterBoundsTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void bucketMapIsHardCappedWhenClientsRotateKeys() {
        // With real headroom per bucket the eviction pass removes non-blocked buckets and the
        // map stays at the configured cap.
        HelpfulVoteRateLimiter limiter = new HelpfulVoteRateLimiter(22, 60, 100, FIXED_CLOCK);

        for (int i = 0; i < 2_000; i++) {
            limiter.tryAcquire("198.18.0." + i);
        }

        assertThat(limiter.trackedBuckets())
                .as("bounded map must never exceed its configured maximum")
                .isLessThanOrEqualTo(100);
        assertThat(limiter.trackedBuckets()).isPositive();
    }

    @Test
    void blockedBucketsAreNotEvictedBeforeBucketsWithBudget() {
        // Evicting a bucket that is over its limit would hand that client a fresh allowance,
        // so the eviction pass keeps blocked buckets while non-blocked ones remain.
        HelpfulVoteRateLimiter limiter = new HelpfulVoteRateLimiter(1, 60, 100, FIXED_CLOCK);
        for (int i = 0; i < 120; i++) {
            limiter.tryAcquire("blocked-" + i); // each key is immediately at its limit
        }
        String blockedKey = "blocked-17";
        assertThat(limiter.tryAcquire(blockedKey))
                .as("blocked bucket must stay blocked while the map is within its hard bound")
                .isFalse();
    }

    @Test
    void emergencyBoundCapsTheMapEvenWhenEveryBucketIsBlocked() {
        HelpfulVoteRateLimiter limiter = new HelpfulVoteRateLimiter(1, 60, 100, FIXED_CLOCK);
        for (int i = 0; i < 1_000; i++) {
            limiter.tryAcquire("blocked-" + i);
        }
        assertThat(limiter.trackedBuckets())
                .as("emergency bound keeps memory finite even when all buckets are blocked")
                .isLessThanOrEqualTo(200);
    }

    @Test
    void attemptsForOneKeyAreStillWithinTheSlidingWindow() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(3, 60, 100, FIXED_CLOCK);

        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
        assertThat(limiter.recordedAttempts("203.0.113.9")).isEqualTo(3);
    }

    @Test
    void adminLoginThrottleKeepsFailingClosed() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(2, 60, 100, FIXED_CLOCK);

        assertThat(limiter.tryAcquire("admin:203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("admin:203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("admin:203.0.113.9")).isFalse();
    }

    @Test
    void loginPageThrottleCountsPageLoadsOnly() {
        AdminLoginPageRateLimiter limiter = new AdminLoginPageRateLimiter(2, 60, 100, FIXED_CLOCK);

        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
    }

    @Test
    void resetAndRecordedAttemptsHooksBehave() {
        HelpfulVoteRateLimiter limiter = new HelpfulVoteRateLimiter(1, 60, 100, FIXED_CLOCK);
        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
        assertThat(limiter.recordedAttempts("203.0.113.9")).isEqualTo(1);
        limiter.reset();
        assertThat(limiter.recordedAttempts("203.0.113.9")).isZero();
        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
    }

    @Test
    void invalidConfigurationIsClampedToUsableValues() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(0, 0, 0, FIXED_CLOCK);
        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
    }
}
