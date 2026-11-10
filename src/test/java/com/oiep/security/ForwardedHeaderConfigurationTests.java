package com.oiep.security;

import com.oiep.IntegrationTestSupport;
import com.oiep.web.ClientAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration drift guard for the headline finding: the Spring {@code framework}
 * forwarded-header mode derives the client address from a client-controllable header and
 * would silently re-open the rate-limit bypass. The application must ship the native
 * RemoteIpValve mode and must not trust {@code CF-Connecting-IP} by default.
 */
class ForwardedHeaderConfigurationTests extends IntegrationTestSupport {

    @Autowired
    private Environment environment;

    @Autowired
    private ClientAddressResolver clientAddressResolver;

    @Test
    void springForwardedHeaderFilterModeIsNeverUsed() {
        assertThat(environment.getProperty("server.forward-headers-strategy"))
                .as("framework mode would make per-client throttles spoofable")
                .isEqualTo("native");
    }

    @Test
    void nativeValveReadsTheCloudflareHeaderNotXForwardedFor() {
        assertThat(environment.getProperty("server.tomcat.remoteip.remote-ip-header"))
                .isEqualTo("CF-Connecting-IP");
    }

    @Test
    void cloudflareTrustIsOffUnlessExplicitlyEnabled() {
        assertThat(clientAddressResolver.trustsCloudflareConnectingIp()).isFalse();
    }
}
