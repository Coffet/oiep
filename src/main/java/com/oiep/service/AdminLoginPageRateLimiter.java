package com.oiep.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Per-client throttle for the admin sign-in page.
 *
 * <p>The login page needs a server-side session to carry its CSRF token, which makes an
 * unauthenticated {@code GET /admin/login} a session-allocation primitive. Without a limit, a
 * single client can allocate an unbounded number of sessions and exhaust the JVM heap. The
 * limiter bounds how many login pages (and therefore sessions) one client can request; the
 * session-capacity filter provides the hard, global ceiling.</p>
 */
@Component
public class AdminLoginPageRateLimiter extends SlidingWindowRateLimiter {

    @Autowired
    public AdminLoginPageRateLimiter(
            @Value("${oiep.admin.login.page.max-requests:30}") int maxRequests,
            @Value("${oiep.admin.login.page.window-seconds:600}") long windowSeconds,
            @Value("${oiep.admin.login.page.max-entries:10000}") int maxEntries) {
        this(maxRequests, windowSeconds, maxEntries, Clock.systemUTC());
    }

    AdminLoginPageRateLimiter(int maxRequests, long windowSeconds, int maxEntries, Clock clock) {
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
