package com.oiep.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Per-client throttle for the anonymous "this helped" action.
 *
 * <p>Useful votes are tracked per browser session, which keeps honest visitors from double
 * voting but does not stop a client that simply discards its cookie. Without a network-level
 * limit, an attacker can inflate a note's useful counter arbitrarily (and create an unbounded
 * number of server sessions while doing so). This limiter bounds both.</p>
 */
@Component
public class HelpfulVoteRateLimiter extends SlidingWindowRateLimiter {

    @Autowired
    public HelpfulVoteRateLimiter(
            @Value("${oiep.helpful.rate-limit.max-requests:30}") int maxRequests,
            @Value("${oiep.helpful.rate-limit.window-seconds:3600}") long windowSeconds,
            @Value("${oiep.helpful.rate-limit.max-entries:10000}") int maxEntries) {
        this(maxRequests, windowSeconds, maxEntries, Clock.systemUTC());
    }

    HelpfulVoteRateLimiter(int maxRequests, long windowSeconds, int maxEntries, Clock clock) {
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
