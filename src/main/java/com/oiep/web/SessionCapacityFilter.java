package com.oiep.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hard ceiling on server-side sessions, guarding against anonymous session-exhaustion DoS.
 *
 * <p>Three endpoints create a session for requests that arrive without one: {@code GET/POST
 * /admin/login} (CSRF token storage), {@code POST /reports} (flash message) and
 * {@code POST /reports/{id}/helpful} (once-per-session vote tracking). Each session consumes
 * heap for the session timeout window, so a client that throws away cookies can allocate
 * sessions at request rate.</p>
 *
 * <p>Admission is atomic: before dispatching a cookie-less request that may create a session,
 * the filter reserves a slot with a compare-and-set loop against
 * {@code activeSessions + pendingAllocations}. The reservation is released when the request
 * completes and is converted into a counted session by the {@link HttpSessionListener}
 * callback. A concurrent burst therefore cannot overshoot the configured cap the way a
 * check-then-act guard could. Existing sessions and endpoints that do not create sessions are
 * unaffected.</p>
 */
public class SessionCapacityFilter implements Filter, HttpSessionListener {

    private static final Logger log = LoggerFactory.getLogger(SessionCapacityFilter.class);

    /**
     * Marks the current request thread as holding an unconverted session-allocation
     * reservation. When the container creates the session on this thread the reservation is
     * converted into the live-session count immediately; otherwise the {@code finally} block
     * in {@link #doFilter} releases it once the request completes. Without this, an in-flight
     * request would be counted both as pending and as active (double-counting and
     * under-admission).
     */
    private static final ThreadLocal<Boolean> RESERVED_SESSION_SLOT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final int maxActiveSessions;
    private final AtomicInteger activeSessions = new AtomicInteger();
    private final AtomicInteger pendingAllocations = new AtomicInteger();

    public SessionCapacityFilter(int maxActiveSessions) {
        this.maxActiveSessions = Math.max(1, maxActiveSessions);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse
                && wouldCreateSession(httpRequest) && httpRequest.getSession(false) == null) {
            if (!reserveAllocationSlot()) {
                log.warn("Refusing to allocate a new session: {} active + {} pending sessions reached the configured cap of {}",
                        activeSessions.get(), pendingAllocations.get(), maxActiveSessions);
                httpResponse.setHeader("Cache-Control", "no-store");
                httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Session capacity reached");
                return;
            }
            RESERVED_SESSION_SLOT.set(Boolean.TRUE);
            try {
                chain.doFilter(request, response);
            } finally {
                // If the session was created, the listener already converted the reservation
                // into the active count and cleared the flag. Otherwise release it here.
                if (Boolean.TRUE.equals(RESERVED_SESSION_SLOT.get())) {
                    pendingAllocations.decrementAndGet();
                }
                RESERVED_SESSION_SLOT.remove();
            }
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Atomically reserves one session-creation slot, or returns {@code false} when the cap has
     * been reached. The reservation is converted by {@link #sessionCreated} or released in the
     * caller's {@code finally} block, so it can never leak.
     */
    private boolean reserveAllocationSlot() {
        while (true) {
            int committed = activeSessions.get();
            int pending = pendingAllocations.get();
            if (committed + pending >= maxActiveSessions) {
                return false;
            }
            if (pendingAllocations.compareAndSet(pending, pending + 1)) {
                return true;
            }
            // Another request changed the pending count in between; retry with fresh values.
        }
    }

    /** Package-private for tests: the routes that allocate a session for cookie-less requests. */
    public static boolean wouldCreateSession(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if ("/admin/login".equals(path)) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if ("/reports".equals(path)) {
            return true;
        }
        return path.startsWith("/reports/") && path.endsWith("/helpful");
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        int count = activeSessions.incrementAndGet();
        // Convert this request's allocation reservation into the live-session count, so the
        // in-flight request is not counted twice (pending + active).
        if (Boolean.TRUE.equals(RESERVED_SESSION_SLOT.get())) {
            pendingAllocations.updateAndGet(current -> Math.max(0, current - 1));
            RESERVED_SESSION_SLOT.set(Boolean.FALSE);
        }
        if (count == maxActiveSessions) {
            log.warn("Active sessions reached the configured cap of {}", maxActiveSessions);
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        activeSessions.updateAndGet(current -> Math.max(0, current - 1));
    }

    /** Live session count (test/support hook). */
    public int getActiveSessionCount() {
        return activeSessions.get();
    }

    /** In-flight session-allocation reservations (test/support hook). */
    public int getPendingAllocationCount() {
        return pendingAllocations.get();
    }
}
