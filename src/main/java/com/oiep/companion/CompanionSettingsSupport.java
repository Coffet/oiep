package com.oiep.companion;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Validation helpers for the optional AI companion configuration.
 *
 * <p>Everything here is pure and side-effect free so the same rules are applied by the
 * {@link CompanionConditions} (which decide whether a component bean exists) and by the
 * components themselves (which fail closed on an unusable value). No secret is ever logged:
 * the helpers only return a short, non-sensitive reason string.</p>
 */
public final class CompanionSettingsSupport {

    /**
     * Values copied verbatim from OIEP's own documentation/example files are not secrets.
     * The companion's own development defaults are not listed here: only obvious
     * "paste something here" placeholders shipped by this repository are rejected.
     */
    private static final Set<String> DOCUMENTED_PLACEHOLDER_SECRETS = Set.of(
            "<paste-your-own-long-random-secret>",
            "<paste-the-operator-token>",
            "paste-the-operator-token",
            "paste-the-secret-you-generated-here",
            "replace-with-the-operator-token",
            "replace-with-a-long-random-password",
            "choose-a-long-random-password",
            "change-me",
            "changeme");

    private static final int MIN_SECRET_LENGTH = 16;
    private static final int MAX_SECRET_LENGTH = 512;

    private CompanionSettingsSupport() {
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * The chat widget URL to embed: an explicit {@code widget-url} wins, otherwise the
     * companion service base URL plus {@code /ui/}. Empty when neither is configured.
     */
    public static String effectiveWidgetUrl(String widgetUrl, String baseUrl) {
        String widget = trimToEmpty(widgetUrl);
        if (!widget.isEmpty()) {
            return widget;
        }
        String base = trimToEmpty(baseUrl);
        if (base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base + "ui/" : base + "/ui/";
    }

    /**
     * The scheme/host/port origin of an http(s) URL, e.g. {@code https://ai.example.org}.
     * Returns an empty string for anything that is not a plain http(s) origin (no userinfo,
     * no credentials, no unparsable value). Paths on the input are allowed: only the origin
     * is returned.
     */
    public static String originOf(String url) {
        String candidate = trimToEmpty(url);
        if (candidate.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return "";
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }
            if (uri.getUserInfo() != null) {
                return "";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String authority = port == -1 ? host : host + ":" + port;
            return scheme + "://" + authority;
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    /**
     * Origin of the companion service base URL. The base URL must be an origin (optionally
     * with a trailing slash): the companion API uses absolute {@code /v1/...} paths and a
     * dedicated hostname is the documented deployment shape. Returns "" when invalid.
     */
    public static String serviceOrigin(String baseUrl) {
        String candidate = trimToEmpty(baseUrl);
        if (candidate.isEmpty()) {
            return "";
        }
        String origin = originOf(candidate);
        if (origin.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(candidate);
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                return "";
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                return "";
            }
        } catch (URISyntaxException ex) {
            return "";
        }
        return origin;
    }

    /** True when both URLs are configured and belong to the same origin. */
    public static boolean sameOrigin(String first, String second) {
        String firstOrigin = originOf(first);
        return !firstOrigin.isEmpty() && firstOrigin.equals(originOf(second));
    }

    /**
     * A token is usable when it is present, is not a documented placeholder and is long
     * enough to be a real secret. Returns the reason otherwise (safe to log).
     */
    public static String secretProblem(String value) {
        String candidate = trimToEmpty(value);
        if (candidate.isEmpty()) {
            return "not set";
        }
        if (DOCUMENTED_PLACEHOLDER_SECRETS.contains(candidate.toLowerCase(Locale.ROOT))) {
            return "set to a documented placeholder, not a secret";
        }
        if (candidate.length() < MIN_SECRET_LENGTH) {
            return "shorter than " + MIN_SECRET_LENGTH + " characters";
        }
        if (candidate.length() > MAX_SECRET_LENGTH) {
            return "longer than " + MAX_SECRET_LENGTH + " characters";
        }
        return "";
    }

    public static boolean isUsableSecret(String value) {
        return secretProblem(value).isEmpty();
    }
}
