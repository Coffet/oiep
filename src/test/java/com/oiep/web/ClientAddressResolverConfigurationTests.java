package com.oiep.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Spring {@code framework} forwarded-header mode derives the client address from a
 * client-controllable header, so it must be rejected at startup. The native Tomcat
 * RemoteIpValve configuration is the only supported mode.
 */
class ClientAddressResolverConfigurationTests {

    @Test
    void frameworkForwardedHeaderStrategyIsRejectedAtStartup() {
        ClientAddressResolver resolver = new ClientAddressResolver(false,
                new MockEnvironment().withProperty("server.forward-headers-strategy", "framework"));

        assertThatThrownBy(resolver::validateForwardedHeaderConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forward-headers-strategy");
    }

    @Test
    void frameworkForwardedHeaderStrategyIsRejectedEvenWithCloudflareTrustFlag() {
        ClientAddressResolver resolver = new ClientAddressResolver(true,
                new MockEnvironment().withProperty("server.forward-headers-strategy", "framework"));

        assertThatThrownBy(resolver::validateForwardedHeaderConfiguration)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nativeAndDisabledForwardedHeaderStrategiesAreAccepted() {
        assertThatCode(() -> new ClientAddressResolver(false,
                new MockEnvironment().withProperty("server.forward-headers-strategy", "native"))
                .validateForwardedHeaderConfiguration())
                .doesNotThrowAnyException();
        assertThatCode(() -> new ClientAddressResolver(false, new MockEnvironment())
                .validateForwardedHeaderConfiguration())
                .doesNotThrowAnyException();
    }

    @Test
    void trustFlagIsExposedForDiagnostics() {
        assertThat(new ClientAddressResolver(true, new MockEnvironment()).trustsCloudflareConnectingIp()).isTrue();
        assertThat(new ClientAddressResolver(false, new MockEnvironment()).trustsCloudflareConnectingIp()).isFalse();
    }
}
