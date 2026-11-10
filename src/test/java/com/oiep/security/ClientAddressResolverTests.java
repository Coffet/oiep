package com.oiep.security;

import com.oiep.web.ClientAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the client-address trust boundary: per-client throttles and security
 * logs must never key on a client-supplied forwarding header.
 */
class ClientAddressResolverTests {

    @Test
    void xForwardedForIsIgnoredWhenCloudflareModeIsOff() {
        ClientAddressResolver resolver = new ClientAddressResolver(false, new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.18.0.1, 203.0.113.9");
        request.addHeader("Forwarded", "for=198.18.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void cloudflareConnectingIpIsUsedOnlyInTrustedMode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Simulate the native RemoteIpValve having validated the trusted proxy hop: it
        // rewrites getRemoteAddr() to the CF-Connecting-IP value.
        request.setRemoteAddr("198.51.100.23");
        request.addHeader(ClientAddressResolver.CLOUDFLARE_CONNECTING_IP_HEADER, "198.51.100.23");

        assertThat(new ClientAddressResolver(true, new MockEnvironment()).resolve(request))
                .isEqualTo("198.51.100.23");
        assertThat(new ClientAddressResolver(false, new MockEnvironment()).resolve(request))
                .isEqualTo("198.51.100.23");
    }

    @Test
    void spoofedCloudflareHeaderIsIgnoredWhenItDoesNotMatchTheSocketPeer() {
        // The valve did not trust the peer (e.g. the app port was accidentally exposed), so
        // getRemoteAddr() is the real peer while the client supplied a forged header.
        ClientAddressResolver resolver = new ClientAddressResolver(true, new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader(ClientAddressResolver.CLOUDFLARE_CONNECTING_IP_HEADER, "198.51.100.23");

        assertThat(resolver.resolve(request))
                .as("a forged header must not create a fresh throttle bucket")
                .isEqualTo("203.0.113.50");
    }

    @Test
    void invalidCloudflareHeaderFallsBackToTheSocketAddress() {
        ClientAddressResolver resolver = new ClientAddressResolver(true, new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.2");

        for (String hostile : new String[]{"<script>alert(1)</script>", "evil.example", "1.2.3.999",
                "198.51.100.23%0d%0aX-Injected: 1"}) {
            request.removeHeader(ClientAddressResolver.CLOUDFLARE_CONNECTING_IP_HEADER);
            request.addHeader(ClientAddressResolver.CLOUDFLARE_CONNECTING_IP_HEADER, hostile);
            assertThat(resolver.resolve(request))
                    .as("hostile CF-Connecting-IP value %s must fall back", hostile)
                    .isEqualTo("172.18.0.2");
        }
    }

    @Test
    void ipLiteralsAreNormalisedAndHostnamesRejected() {
        assertThat(ClientAddressResolver.normalizeAddress("203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(ClientAddressResolver.normalizeAddress("  203.0.113.9  ")).isEqualTo("203.0.113.9");
        assertThat(ClientAddressResolver.normalizeAddress("010.001.002.003")).isEqualTo("10.1.2.3");
        assertThat(ClientAddressResolver.normalizeAddress("2001:0DB8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(ClientAddressResolver.normalizeAddress("::1")).isEqualTo("0:0:0:0:0:0:0:1");
        assertThat(ClientAddressResolver.normalizeAddress("::ffff:192.0.2.1"))
                .isIn("192.0.2.1", "0:0:0:0:0:ffff:192.0.2.1", "0:0:0:0:0:ffff:c000:201");
        assertThat(ClientAddressResolver.normalizeAddress("evil.example")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("   ")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("1".repeat(100))).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("1.2.3.999")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("1.2.3.4.5")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("1.2.3.-4")).isNull();
    }

    @Test
    void hexLikeHostnamesAreRejectedWithoutAnyResolverLookup() {
        // These values used to satisfy a permissive "[0-9A-Fa-f:.]+" check and could reach
        // InetAddress.getByName() (a DNS lookup). Strict literal parsing rejects them.
        assertThat(ClientAddressResolver.normalizeAddress("beef")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("dead.beef")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("cafe")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("::g")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("1:::2")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("1::2::3")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("12345::1")).isNull();
        assertThat(ClientAddressResolver.normalizeAddress("1:2:3:4:5:6:7:8:9")).isNull();
    }

    @Test
    void missingSocketAddressCollapsesToUnknown() {
        ClientAddressResolver resolver = new ClientAddressResolver(false, new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertThat(resolver.resolve(request)).isEqualTo("unknown");
    }
}
