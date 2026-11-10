package com.oiep.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * Rejects cross-site browser requests to anonymous, state-changing endpoints.
 *
 * <p>The public debrief form and the useful-vote action are not tied to an authenticated
 * session, so there is no CSRF token to bind; that does not mean an attacker's page should be
 * able to submit them with a visitor's browser. This validator enforces the standard
 * stateless CSRF defence:</p>
 *
 * <ol>
 *   <li>If {@code Origin} is present, it must match this application's host (browsers attach
 *       it to all cross-origin and same-origin POSTs).</li>
 *   <li>Otherwise {@code Sec-Fetch-Site} must not describe a cross-site request.</li>
 *   <li>Otherwise the {@code Referer} host is checked.</li>
 *   <li>When none of the three headers is present the request is treated as a non-browser
 *       client (curl, a script, the test suite). Those requests carry no ambient browser
 *       credentials, so they are allowed.</li>
 * </ol>
 *
 * <p>Ports are compared leniently because TLS usually terminates at the proxy: an origin on
 * the default port for its scheme is accepted regardless of the internal port the app sees,
 * while a non-default origin port must match the request's server port.</p>
 */
@Component
public class SameOriginPostValidator {

    /** Returns {@code true} when a state-changing request comes from this application. */
    public boolean isSameOrigin(HttpServletRequest request) {
        String origin = trimToNull(request.getHeader("Origin"));
        if (origin != null) {
            return matchesRequestOrigin(origin, request);
        }
        String fetchSite = trimToNull(request.getHeader("Sec-Fetch-Site"));
        if (fetchSite != null) {
            return "same-origin".equalsIgnoreCase(fetchSite) || "none".equalsIgnoreCase(fetchSite);
        }
        String referer = trimToNull(request.getHeader("Referer"));
        if (referer != null) {
            return matchesRequestOrigin(referer, request);
        }
        // No browser provenance headers at all: a non-browser client.
        return true;
    }

    private static boolean matchesRequestOrigin(String headerValue, HttpServletRequest request) {
        URI uri;
        try {
            uri = URI.create(headerValue);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            return false;
        }
        String originHost = uri.getHost();
        if (originHost.startsWith("[") && originHost.endsWith("]")) {
            originHost = originHost.substring(1, originHost.length() - 1);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return false;
        }
        // A request that reached the app over HTTPS (directly or through a trusted forwarding
        // hop) must not accept a plain-http Origin: that is a cross-scheme/downgrade Origin.
        if (request.isSecure() && "http".equals(scheme)) {
            return false;
        }
        if (!matchesRequestHost(originHost, request)) {
            return false;
        }
        int originPort = uri.getPort();
        if (originPort == -1) {
            // Default port for the origin scheme (80/443). Accept: the app may see an
            // internal proxy port behind TLS termination.
            return true;
        }
        return originPort == request.getServerPort();
    }

    private static boolean matchesRequestHost(String originHost, HttpServletRequest request) {
        // Prefer the Host header: it is what the browser sent to the proxy and cannot be set by
        // cross-site JavaScript. Only fall back to the server name (which forwarded-header
        // handling can rewrite) when the header is absent.
        String hostHeader = request.getHeader("Host");
        if (hostHeader != null) {
            String host = hostHeader;
            if (host.startsWith("[")) {
                // IPv6 literal: [::1]:8080
                int close = host.indexOf(']');
                if (close > 0) {
                    host = host.substring(1, close);
                }
            } else {
                int portSeparator = host.lastIndexOf(':');
                if (portSeparator > 0) {
                    host = host.substring(0, portSeparator);
                }
            }
            if (originHost.equalsIgnoreCase(host.trim())) {
                return true;
            }
        }
        String serverName = request.getServerName();
        return serverName != null && originHost.equalsIgnoreCase(serverName);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
