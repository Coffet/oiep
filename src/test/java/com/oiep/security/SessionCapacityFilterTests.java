package com.oiep.security;

import com.oiep.web.SessionCapacityFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the global session cap that backstops anonymous session exhaustion.
 * The admission decision must be atomic: a concurrent burst of cookie-less session-creating
 * requests may not overshoot the configured ceiling.
 */
class SessionCapacityFilterTests {

    private final MockServletContext servletContext = new MockServletContext();

    @Test
    void sessionCreatingRequestIsRejectedOnceTheCapIsReached() throws Exception {
        SessionCapacityFilter filter = new SessionCapacityFilter(2);
        filter.sessionCreated(event("a"));
        filter.sessionCreated(event("b"));
        assertThat(filter.getActiveSessionCount()).isEqualTo(2);

        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/admin/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(filter.getPendingAllocationCount()).isZero();
    }

    @Test
    void nonSessionEndpointsAndExistingSessionsAreStillServedAtTheCap() throws Exception {
        SessionCapacityFilter filter = new SessionCapacityFilter(1);
        filter.sessionCreated(event("a"));

        MockHttpServletResponse browseResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(servletContext, "GET", "/browse"), browseResponse,
                new MockFilterChain());
        assertThat(browseResponse.getStatus()).isEqualTo(200);

        MockHttpServletRequest authenticated = new MockHttpServletRequest(servletContext, "GET", "/admin/login");
        authenticated.setSession(new MockHttpSession(servletContext, "existing"));
        MockHttpServletResponse authenticatedResponse = new MockHttpServletResponse();
        filter.doFilter(authenticated, authenticatedResponse, new MockFilterChain());
        assertThat(authenticatedResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void destroyedSessionsFreeCapacityAgain() throws Exception {
        SessionCapacityFilter filter = new SessionCapacityFilter(1);
        MockHttpSession session = new MockHttpSession(servletContext, "a");
        filter.sessionCreated(new HttpSessionEvent(session));
        filter.sessionDestroyed(new HttpSessionEvent(session));
        assertThat(filter.getActiveSessionCount()).isZero();

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(servletContext, "GET", "/admin/login"), response,
                new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void reservationIsReleasedWhenTheRequestCreatesNoSession() throws Exception {
        SessionCapacityFilter filter = new SessionCapacityFilter(1);

        // First request passes through without creating a session: its slot must be returned.
        MockHttpServletResponse noSession = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(servletContext, "GET", "/admin/login"), noSession,
                (request, response) -> { /* controller rejects/does not create a session */ });
        assertThat(noSession.getStatus()).isEqualTo(200);
        assertThat(filter.getPendingAllocationCount()).isZero();
        assertThat(filter.getActiveSessionCount()).isZero();

        // The single slot is still available for a request that really creates a session.
        MockHttpServletResponse createsSession = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(servletContext, "GET", "/admin/login"), createsSession,
                (request, response) -> filter.sessionCreated(new HttpSessionEvent(
                        ((MockHttpServletRequest) request).getSession(true))));
        assertThat(createsSession.getStatus()).isEqualTo(200);
        assertThat(filter.getActiveSessionCount()).isEqualTo(1);

        // ...and the cap now refuses the next cookie-less allocation.
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(servletContext, "GET", "/admin/login"), rejected,
                new MockFilterChain());
        assertThat(rejected.getStatus()).isEqualTo(503);
    }

    @Test
    void concurrentBurstCannotOvershootTheCap() throws Exception {
        int cap = 5;
        int attempts = 16;
        SessionCapacityFilter filter = new SessionCapacityFilter(cap);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch admittedInChain = new CountDownLatch(cap);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                MockHttpServletRequest request =
                        new MockHttpServletRequest(servletContext, "GET", "/admin/login");
                MockHttpServletResponse response = new MockHttpServletResponse();
                results.add(executor.submit(() -> {
                    start.await();
                    filter.doFilter(request, response, (req, res) -> {
                        HttpSession session = ((MockHttpServletRequest) req).getSession(true);
                        filter.sessionCreated(new HttpSessionEvent(session));
                        admittedInChain.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    return response.getStatus();
                }));
            }
            start.countDown();
            assertThat(admittedInChain.await(10, TimeUnit.SECONDS))
                    .as("exactly the cap's worth of requests should reach the session-creating chain")
                    .isTrue();
            release.countDown();

            int admitted = 0;
            int rejected = 0;
            for (Future<Integer> result : results) {
                int status = result.get(10, TimeUnit.SECONDS);
                if (status == 503) {
                    rejected++;
                } else {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(cap);
            assertThat(rejected).isEqualTo(attempts - cap);
            assertThat(filter.getActiveSessionCount()).isEqualTo(cap);
            assertThat(filter.getPendingAllocationCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyStateChangingOrLoginRoutesAreTreatedAsSessionCreating() {
        assertThat(SessionCapacityFilter.wouldCreateSession(
                new MockHttpServletRequest(servletContext, "GET", "/admin/login"))).isTrue();
        assertThat(SessionCapacityFilter.wouldCreateSession(
                new MockHttpServletRequest(servletContext, "POST", "/reports"))).isTrue();
        assertThat(SessionCapacityFilter.wouldCreateSession(
                new MockHttpServletRequest(servletContext, "POST", "/reports/7/helpful"))).isTrue();
        assertThat(SessionCapacityFilter.wouldCreateSession(
                new MockHttpServletRequest(servletContext, "GET", "/reports/7"))).isFalse();
        assertThat(SessionCapacityFilter.wouldCreateSession(
                new MockHttpServletRequest(servletContext, "GET", "/browse"))).isFalse();
    }

    private HttpSessionEvent event(String id) {
        return new HttpSessionEvent(new MockHttpSession(servletContext, id));
    }
}
