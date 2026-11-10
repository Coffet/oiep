package com.oiep.web;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the client address used for rate limiting and security logging.
 *
 * <p>{@code X-Forwarded-For} is deliberately <strong>not</strong> trusted: it is
 * client-controllable at the outermost proxy (a visitor can send their own value and the
 * proxy appends it), and keying throttles on it lets an attacker rotate the header to bypass
 * every per-client limit. Spring's {@code server.forward-headers-strategy=framework} derives
 * {@code getRemoteAddr()} from that same header, so that mode is rejected at startup: there is
 * no safe way to use it for per-client throttles.</p>
 *
 * <p>When the instance is deployed behind Cloudflare Tunnel (the shipped Docker setup), the
 * app port is bound to loopback and only {@code cloudflared} can reach it.
 * {@code CF-Connecting-IP} is then set by Cloudflare's edge and cannot be spoofed by visitors.
 * The header is honoured only when
 * {@code oiep.security.trust-cloudflare-connecting-ip=true} is set explicitly <em>and</em>
 * Tomcat's native trusted-proxy handling has already rewritten the peer address to the same
 * value, so a directly-exposed instance cannot get fresh throttle buckets from a spoofed
 * header.</p>
 */
@Component
public class ClientAddressResolver {

    /** Set by Cloudflare's edge for tunnel/proxied traffic. */
    public static final String CLOUDFLARE_CONNECTING_IP_HEADER = "CF-Connecting-IP";

    private static final Logger log = LoggerFactory.getLogger(ClientAddressResolver.class);

    /** Longest textual IPv6 address (including an IPv4-mapped form) plus slack. */
    private static final int MAX_ADDRESS_LENGTH = 45;

    private static final Pattern IPV4_LITERAL = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:]+$");

    private final boolean trustCloudflareConnectingIp;
    private final Environment environment;

    public ClientAddressResolver(
            @Value("${oiep.security.trust-cloudflare-connecting-ip:false}") boolean trustCloudflareConnectingIp,
            Environment environment) {
        this.trustCloudflareConnectingIp = trustCloudflareConnectingIp;
        this.environment = environment;
    }

    /**
     * Refuses the unsafe Spring forwarded-header mode. {@code framework} rewrites
     * {@code getRemoteAddr()} from the client-supplied {@code X-Forwarded-For}/RFC 7239
     * {@code Forwarded} chain, which would silently defeat every per-client throttle. The
     * native Tomcat {@code RemoteIpValve} configuration in {@code application.properties} is
     * the supported way to learn the client address from a trusted proxy.
     */
    @PostConstruct
    void validateForwardedHeaderConfiguration() {
        if (isSpringForwardedHeaderFilterEnabled()) {
            throw new IllegalStateException(
                    "server.forward-headers-strategy=framework is not supported: it derives the "
                            + "client address from the client-controllable X-Forwarded-For/Forwarded "
                            + "headers, which would defeat every per-client throttle. Use the shipped "
                            + "native RemoteIpValve configuration (server.forward-headers-strategy=native "
                            + "with CF-Connecting-IP) or leave forwarded headers disabled.");
        }
        if (trustCloudflareConnectingIp) {
            log.info("Trusting CF-Connecting-IP for client-address resolution "
                    + "(requires the native RemoteIpValve hop and a loopback-bound app port).");
        }
    }

    public boolean trustsCloudflareConnectingIp() {
        return trustCloudflareConnectingIp;
    }

    /**
     * Returns a normalised client address for throttling and logging. Never returns
     * {@code null}; unresolvable values collapse to {@code "unknown"}.
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeAddress(request.getRemoteAddr());
        if (trustCloudflareConnectingIp) {
            String cloudflareAddress = normalizeAddress(request.getHeader(CLOUDFLARE_CONNECTING_IP_HEADER));
            // Only honour the header when Tomcat's native RemoteIpValve has already validated
            // that the immediate peer is a trusted proxy and rewritten getRemoteAddr() to the
            // same address. If the app port were ever exposed directly, a client's spoofed
            // CF-Connecting-IP would not match its socket address and is ignored here, so the
            // trust switch cannot be abused to get a fresh throttle bucket.
            if (cloudflareAddress != null && cloudflareAddress.equals(remoteAddress)) {
                return cloudflareAddress;
            }
        }
        return remoteAddress != null ? remoteAddress : "unknown";
    }

    /**
     * Normalises an IP literal to a canonical textual form without ever performing a DNS
     * lookup. Returns {@code null} for blank, oversized, hostname-like or unparseable values so
     * they can never become unbounded map keys or inject newlines into a log line.
     */
    public static String normalizeAddress(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String value = candidate.trim();
        if (value.isEmpty() || value.length() > MAX_ADDRESS_LENGTH) {
            return null;
        }
        String ipv4 = normalizeIpv4(value);
        if (ipv4 != null) {
            return ipv4;
        }
        return normalizeIpv6(value);
    }

    /** Strict IPv4 literal parsing; never resolves names or applies octal interpretation. */
    private static String normalizeIpv4(String value) {
        Matcher matcher = IPV4_LITERAL.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        StringBuilder canonical = new StringBuilder(value.length());
        for (int group = 1; group <= 4; group++) {
            int octet;
            try {
                octet = Integer.parseInt(matcher.group(group));
            } catch (NumberFormatException ex) {
                return null;
            }
            if (octet > 255) {
                return null;
            }
            if (group > 1) {
                canonical.append('.');
            }
            canonical.append(octet);
        }
        return canonical.toString();
    }

    /**
     * Strict IPv6 literal parsing: validates the textual structure itself (so a hex-like
     * hostname such as {@code beef} can never reach a resolver) and only then asks the JDK to
     * canonicalise the already-validated literal.
     */
    private static String normalizeIpv6(String value) {
        if (value.indexOf(':') < 0) {
            return null;
        }
        String candidate = value;
        // Convert an embedded IPv4 tail (e.g. ::ffff:192.0.2.1) to two hex groups.
        int lastColon = candidate.lastIndexOf(':');
        String tail = candidate.substring(lastColon + 1);
        if (tail.indexOf('.') >= 0) {
            String ipv4 = normalizeIpv4(tail);
            if (ipv4 == null) {
                return null;
            }
            String[] octets = ipv4.split("\\.");
            String mapped = String.format("%02x%02x:%02x%02x",
                    Integer.parseInt(octets[0]), Integer.parseInt(octets[1]),
                    Integer.parseInt(octets[2]), Integer.parseInt(octets[3]));
            candidate = candidate.substring(0, lastColon + 1) + mapped;
        }
        if (!IPV6_CHARS.matcher(candidate).matches()) {
            return null;
        }
        String[] groups = expandIpv6Groups(candidate);
        if (groups == null) {
            return null;
        }
        byte[] address = new byte[16];
        for (int i = 0; i < 8; i++) {
            int groupValue;
            try {
                groupValue = Integer.parseInt(groups[i], 16);
            } catch (NumberFormatException ex) {
                return null;
            }
            address[i * 2] = (byte) ((groupValue >> 8) & 0xff);
            address[i * 2 + 1] = (byte) (groupValue & 0xff);
        }
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    /** Expands a validated textual IPv6 address into exactly eight 1-4 digit hex groups. */
    private static String[] expandIpv6Groups(String value) {
        String[] groups = new String[8];
        int doubleColon = value.indexOf("::");
        if (doubleColon < 0) {
            String[] parts = value.split(":", -1);
            if (parts.length != 8) {
                return null;
            }
            System.arraycopy(parts, 0, groups, 0, 8);
        } else {
            if (value.indexOf("::", doubleColon + 2) >= 0) {
                return null;
            }
            String left = value.substring(0, doubleColon);
            String right = value.substring(doubleColon + 2);
            String[] leftGroups = left.isEmpty() ? new String[0] : left.split(":", -1);
            String[] rightGroups = right.isEmpty() ? new String[0] : right.split(":", -1);
            if (leftGroups.length + rightGroups.length >= 8) {
                return null;
            }
            for (int i = 0; i < leftGroups.length; i++) {
                groups[i] = leftGroups[i];
            }
            for (int i = 0; i < rightGroups.length; i++) {
                groups[8 - rightGroups.length + i] = rightGroups[i];
            }
            for (int i = leftGroups.length; i < 8 - rightGroups.length; i++) {
                groups[i] = "0";
            }
        }
        for (String group : groups) {
            if (group == null || group.isEmpty() || group.length() > 4) {
                return null;
            }
            for (int i = 0; i < group.length(); i++) {
                if (Character.digit(group.charAt(i), 16) < 0) {
                    return null;
                }
            }
        }
        return groups;
    }

    private boolean isSpringForwardedHeaderFilterEnabled() {
        String strategy = environment.getProperty("server.forward-headers-strategy");
        return strategy != null
                && ("framework".equalsIgnoreCase(strategy) || "true".equalsIgnoreCase(strategy));
    }
}
