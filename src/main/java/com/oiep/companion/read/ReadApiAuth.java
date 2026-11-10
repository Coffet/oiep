package com.oiep.companion.read;

import com.oiep.companion.CompanionConditions;
import com.oiep.companion.CompanionSettingsSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Bearer-token authentication for the read-only experiences API.
 *
 * <p>The configured token is supplied by the deployment environment
 * ({@code OIEP_COMPANION_READ_TOKEN}) and grants exactly one scope: reading published
 * experiences. There is no username, no session and no cookie path.</p>
 *
 * <p>The comparison follows the same hardening pattern as
 * {@link com.oiep.service.AdminAuth}: both the configured and the presented token are hashed
 * with SHA-256 first, so {@link MessageDigest#isEqual(byte[], byte[])} only ever compares two
 * fixed-length 32-byte arrays. An attacker can therefore learn neither the token's length nor
 * its content from response-time differences. The token is never logged or echoed.</p>
 */
@Component
@Conditional(CompanionConditions.ReadApi.class)
public class ReadApiAuth {

    private static final String SCHEME = "Bearer";

    private final byte[] expectedDigest;
    private final boolean configured;

    public ReadApiAuth(@Value("${oiep.companion.read-api.token:}") String token) {
        String expected = CompanionSettingsSupport.trimToEmpty(token);
        this.configured = !expected.isEmpty();
        this.expectedDigest = sha256(expected);
    }

    /**
     * True when the {@code Authorization} header is a well-formed bearer credential whose token
     * matches the configured read token. Missing, malformed and wrong tokens all return false.
     */
    public boolean isAuthorized(String authorizationHeader) {
        if (!configured) {
            return false;
        }
        String candidate = bearerToken(authorizationHeader);
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, sha256(candidate));
    }

    /** Extracts the credential from {@code Bearer <token>}; null when the header is malformed. */
    private static String bearerToken(String header) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (value.length() <= SCHEME.length()
                || !value.regionMatches(true, 0, SCHEME, 0, SCHEME.length())
                || value.charAt(SCHEME.length()) != ' ') {
            return null;
        }
        String token = value.substring(SCHEME.length() + 1).trim();
        return token.isEmpty() ? null : token;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            // Every Java runtime is required to provide SHA-256; absence is a fatal
            // misconfiguration rather than a reason to fall back to a weaker comparison.
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }
}
