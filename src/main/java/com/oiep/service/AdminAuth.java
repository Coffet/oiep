package com.oiep.service;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Session-based administrator authentication and CSRF-token helpers.
 *
 * <p>The administrator password is supplied by the deployment environment
 * ({@code OIEP_ADMIN_PASSWORD}) and is never stored in the database or the repository. When
 * the password is unset, the admin area is disabled. Login attempts are compared in constant
 * time; successful login rotates the HTTP session to prevent session fixation.</p>
 */
@Component
public class AdminAuth {

    public static final String AUTHENTICATED_SESSION_KEY = "OIEP_ADMIN_AUTHENTICATED";
    public static final String CSRF_SESSION_KEY = "OIEP_ADMIN_CSRF";

    private static final Logger log = LoggerFactory.getLogger(AdminAuth.class);
    private static final int CSRF_TOKEN_BYTES = 32;

    private final String username;
    private final String password;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminAuth(@Value("${oiep.admin.username:admin}") String username,
                     @Value("${oiep.admin.password:}") String password) {
        this.username = username == null || username.isBlank() ? "admin" : username.trim();
        this.password = password == null ? "" : password;
    }

    @PostConstruct
    void reportConfigurationState() {
        if (!isConfigured()) {
            log.warn("Admin area is disabled: set OIEP_ADMIN_PASSWORD to enable /admin.");
        } else if (password.length() < 12) {
            log.warn("Admin password is shorter than 12 characters; use a longer secret.");
        }
    }

    public boolean isConfigured() {
        return !password.isBlank();
    }

    /** Constant-time credential check. Both the username and password must match. */
    public boolean authenticate(String candidateUsername, String candidatePassword) {
        if (!isConfigured() || candidateUsername == null || candidatePassword == null) {
            return false;
        }
        boolean usernameMatches = constantTimeEquals(username, candidateUsername.trim());
        boolean passwordMatches = constantTimeEquals(password, candidatePassword);
        return usernameMatches & passwordMatches;
    }

    public boolean isAuthenticated(HttpSession session) {
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(AUTHENTICATED_SESSION_KEY));
    }

    /**
     * Rotates the session after successful authentication, then marks it authenticated and
     * issues a fresh CSRF token. The old session is invalidated first.
     */
    public void signIn(HttpServletRequest request) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AUTHENTICATED_SESSION_KEY, Boolean.TRUE);
        session.setAttribute(CSRF_SESSION_KEY, newToken());
    }

    public void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /** Returns the existing CSRF token, creating one for this session if needed. */
    public String csrfToken(HttpSession session) {
        if (session == null) {
            throw new IllegalStateException("An HTTP session is required to issue an admin CSRF token");
        }
        Object existing = session.getAttribute(CSRF_SESSION_KEY);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }
        String token = newToken();
        session.setAttribute(CSRF_SESSION_KEY, token);
        return token;
    }

    public boolean csrfMatches(HttpSession session, String candidate) {
        if (session == null || candidate == null) {
            return false;
        }
        Object expected = session.getAttribute(CSRF_SESSION_KEY);
        return expected instanceof String token && constantTimeEquals(token, candidate);
    }

    private String newToken() {
        byte[] bytes = new byte[CSRF_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
