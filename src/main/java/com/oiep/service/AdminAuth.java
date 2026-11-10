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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

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

    /**
     * Placeholder values that appear in the repository documentation and {@code .env.example}.
     * A deployment that copied one of these instead of generating a real secret would run an
     * admin area protected by a publicly known password, so the admin area stays disabled.
     */
    private static final Set<String> DOCUMENTED_PLACEHOLDER_PASSWORDS = Set.of(
            "replace-with-a-long-random-password",
            "choose-a-long-random-password",
            "paste-a-long-random-password-here",
            "<paste-your-own-long-random-secret>",
            "paste-the-secret-you-generated-here");

    private final String username;
    private final String password;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminAuth(@Value("${oiep.admin.username:admin}") String username,
                     @Value("${oiep.admin.password:}") String password) {
        this.username = username == null || username.isBlank() ? "admin" : username.trim();
        this.password = rejectDocumentedPlaceholder(password);
    }

    /**
     * A documented placeholder is not a secret. Fail closed (disable the admin area) rather
     * than exposing moderation to a password printed in the repository.
     */
    private static String rejectDocumentedPlaceholder(String rawPassword) {
        if (rawPassword == null) {
            return "";
        }
        if (DOCUMENTED_PLACEHOLDER_PASSWORDS.contains(rawPassword.trim().toLowerCase(Locale.ROOT))) {
            log.error("OIEP_ADMIN_PASSWORD is set to a password documented in the repository; "
                    + "the admin area stays disabled. Generate a unique long random secret.");
            return "";
        }
        return rawPassword;
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

    /**
     * Constant-time comparison that does not leak the length of the expected value.
     *
     * <p>{@link MessageDigest#isEqual(byte[], byte[])} only guarantees constant-time behaviour
     * for equal-length arrays, so comparing the raw strings returns immediately when an
     * attacker submits a candidate of a different length. Hashing both inputs with SHA-256
     * first makes both arrays a fixed 32 bytes, so an attacker cannot learn the configured
     * password (or CSRF token) length from response-time differences.</p>
     */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(expected), sha256(actual));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            // Every Java runtime is required to provide SHA-256; treat absence as a fatal
            // misconfiguration rather than silently falling back to a weaker comparison.
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }
}
