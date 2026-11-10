package com.oiep.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * In-memory login throttle for the admin area. Counts attempts per client address and
 * deliberately fails closed once the configured limit is reached. This is friction against
 * password guessing, not a replacement for a reverse-proxy/WAF layer.
 *
 * <p>The client key must come from the trusted client-address resolver, never directly from
 * a client-supplied forwarding header: otherwise an attacker can rotate
 * {@code X-Forwarded-For} and brute-force the admin password without ever hitting the
 * throttle.</p>
 */
@Component
public class AdminLoginRateLimiter extends SlidingWindowRateLimiter {

    @Autowired
    public AdminLoginRateLimiter(
            @Value("${oiep.admin.login.max-attempts:6}") int maxAttempts,
            @Value("${oiep.admin.login.window-seconds:900}") long windowSeconds,
            @Value("${oiep.admin.login.max-entries:10000}") int maxEntries) {
        this(maxAttempts, windowSeconds, maxEntries, Clock.systemUTC());
    }

    AdminLoginRateLimiter(int maxAttempts, long windowSeconds, int maxEntries, Clock clock) {
        super(maxAttempts, windowSeconds, maxEntries, clock);
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
