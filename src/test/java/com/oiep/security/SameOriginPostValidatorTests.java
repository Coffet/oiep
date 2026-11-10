package com.oiep.security;

import com.oiep.web.SameOriginPostValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the stateless CSRF defence on the anonymous POST endpoints.
 */
class SameOriginPostValidatorTests {

    private final SameOriginPostValidator validator = new SameOriginPostValidator();

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("oiep.example");
        request.setServerPort(443);
        request.setSecure(true);
        request.setScheme("https");
        request.addHeader("Host", "oiep.example");
        return request;
    }

    @Test
    void requestsWithoutBrowserProvenanceHeadersAreAllowed() {
        assertThat(validator.isSameOrigin(request())).isTrue();
    }

    @Test
    void sameOriginPostIsAllowed() {
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "https://oiep.example");
        assertThat(validator.isSameOrigin(request)).isTrue();
    }

    @Test
    void foreignOriginIsRejected() {
        for (String origin : new String[]{
                "https://evil.example",
                "https://oiep.example.evil.example",
                "https://evil.example/oiep.example",
                "http://oiep.example@evil.example",
                "null",
                "ftp://oiep.example"}) {
            MockHttpServletRequest request = request();
            request.addHeader("Origin", origin);
            assertThat(validator.isSameOrigin(request))
                    .as("Origin %s must be rejected", origin)
                    .isFalse();
        }
    }

    @Test
    void crossSiteFetchMetadataIsRejected() {
        MockHttpServletRequest crossSite = request();
        crossSite.addHeader("Sec-Fetch-Site", "cross-site");
        assertThat(validator.isSameOrigin(crossSite)).isFalse();

        MockHttpServletRequest sameSite = request();
        sameSite.addHeader("Sec-Fetch-Site", "same-origin");
        assertThat(validator.isSameOrigin(sameSite)).isTrue();

        MockHttpServletRequest directNavigation = request();
        directNavigation.addHeader("Sec-Fetch-Site", "none");
        assertThat(validator.isSameOrigin(directNavigation)).isTrue();
    }

    @Test
    void refererIsUsedWhenOriginAndFetchMetadataAreAbsent() {
        MockHttpServletRequest foreignReferer = request();
        foreignReferer.addHeader("Referer", "https://evil.example/page");
        assertThat(validator.isSameOrigin(foreignReferer)).isFalse();

        MockHttpServletRequest sameReferer = request();
        sameReferer.addHeader("Referer", "https://oiep.example/reports/1");
        assertThat(validator.isSameOrigin(sameReferer)).isTrue();
    }

    @Test
    void nonDefaultOriginPortMustMatchTheRequestPort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setScheme("http");
        request.addHeader("Host", "localhost:8080");

        request.addHeader("Origin", "http://localhost:8080");
        assertThat(validator.isSameOrigin(request)).isTrue();

        request.removeHeader("Origin");
        request.addHeader("Origin", "http://localhost:9999");
        assertThat(validator.isSameOrigin(request)).isFalse();
    }

    @Test
    void defaultPortOriginIsAcceptedBehindTlsTermination() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("oiep.example");
        request.setServerPort(8080);
        request.setScheme("http");
        request.addHeader("Host", "oiep.example");
        request.addHeader("Origin", "https://oiep.example");

        assertThat(validator.isSameOrigin(request)).isTrue();
    }

    @Test
    void ipv6HostHeaderIsComparedWithoutBrackets() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("::1");
        request.setServerPort(8080);
        request.addHeader("Host", "[::1]:8080");
        request.addHeader("Origin", "http://[::1]:8080");

        assertThat(validator.isSameOrigin(request)).isTrue();
    }

    @Test
    void secureRequestRejectsPlainHttpOrigin() {
        MockHttpServletRequest secureRequest = new MockHttpServletRequest();
        secureRequest.setServerName("oiep.example");
        secureRequest.setServerPort(443);
        secureRequest.setScheme("https");
        secureRequest.setSecure(true);
        secureRequest.addHeader("Host", "oiep.example");
        secureRequest.addHeader("Origin", "http://oiep.example");
        assertThat(validator.isSameOrigin(secureRequest)).isFalse();

        secureRequest.removeHeader("Origin");
        secureRequest.addHeader("Origin", "https://oiep.example");
        assertThat(validator.isSameOrigin(secureRequest)).isTrue();
    }
}
