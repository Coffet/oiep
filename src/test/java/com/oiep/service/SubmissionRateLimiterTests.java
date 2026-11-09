package com.oiep.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionRateLimiterTests {

    @Test
    void allowsConfiguredNumberOfRequestsThenBlocks() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(3, 60, 100, clock);

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
        assertThat(limiter.recordedAttempts("1.2.3.4")).isEqualTo(3);
    }

    @Test
    void windowSlides() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(2, 60, 100, clock);

        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isFalse();

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire("client")).isTrue();
    }

    @Test
    void bucketsAreIndependentPerClient() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(1, 60, 100, clock);

        assertThat(limiter.tryAcquire("a.example")).isTrue();
        assertThat(limiter.tryAcquire("a.example")).isFalse();
        assertThat(limiter.tryAcquire("b.example")).isTrue();
    }

    @Test
    void resetForgetsEverything() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(1, 60, 100, clock);

        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isFalse();
        limiter.reset();
        assertThat(limiter.tryAcquire("client")).isTrue();
    }

    @Test
    void blankClientKeyFallsBackToUnknownBucket() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(1, 60, 100, clock);

        assertThat(limiter.tryAcquire(null)).isTrue();
        assertThat(limiter.tryAcquire(null)).isFalse();
        assertThat(limiter.tryAcquire("  ")).isFalse();
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        private MutableClock(Instant start) {
            this.current = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
