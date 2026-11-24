package com.oiep.companion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit cover for the companion configuration helpers. These rules decide whether a
 * sub-feature is allowed to exist at all, so they are tested independently of Spring.
 */
class CompanionSettingsSupportTests {

    @Test
    void effectiveWidgetUrlPrefersTheExplicitWidgetUrlThenTheServiceBaseUrl() {
        assertThat(CompanionSettingsSupport.effectiveWidgetUrl("https://ai.example.org/ui/", ""))
                .isEqualTo("https://ai.example.org/ui/");
        assertThat(CompanionSettingsSupport.effectiveWidgetUrl("", "https://ai.example.org"))
                .isEqualTo("https://ai.example.org/ui/");
        assertThat(CompanionSettingsSupport.effectiveWidgetUrl("", "https://ai.example.org/"))
                .isEqualTo("https://ai.example.org/ui/");
        assertThat(CompanionSettingsSupport.effectiveWidgetUrl("", "")).isEmpty();
        assertThat(CompanionSettingsSupport.effectiveWidgetUrl("  ", " ")).isEmpty();
    }

    @Test
    void originOfAcceptsOnlyPlainHttpOrigins() {
        assertThat(CompanionSettingsSupport.originOf("https://ai.example.org/ui/"))
                .isEqualTo("https://ai.example.org");
        assertThat(CompanionSettingsSupport.originOf("http://127.0.0.1:8090/ui/"))
                .isEqualTo("http://127.0.0.1:8090");
        assertThat(CompanionSettingsSupport.originOf("HTTPS://AI.Example.org:443/x"))
                .isEqualTo("https://ai.example.org:443");

        assertThat(CompanionSettingsSupport.originOf("javascript:alert(1)")).isEmpty();
        assertThat(CompanionSettingsSupport.originOf("file:///etc/passwd")).isEmpty();
        assertThat(CompanionSettingsSupport.originOf("//ai.example.org/ui/")).isEmpty();
        assertThat(CompanionSettingsSupport.originOf("https://user:pass@ai.example.org/ui/")).isEmpty();
        assertThat(CompanionSettingsSupport.originOf("not a url")).isEmpty();
        assertThat(CompanionSettingsSupport.originOf("")).isEmpty();
        assertThat(CompanionSettingsSupport.originOf(null)).isEmpty();
    }

    @Test
    void serviceOriginRequiresAnOriginWithoutPathQueryOrFragment() {
        assertThat(CompanionSettingsSupport.serviceOrigin("https://ai.example.org"))
                .isEqualTo("https://ai.example.org");
        assertThat(CompanionSettingsSupport.serviceOrigin("https://ai.example.org/"))
                .isEqualTo("https://ai.example.org");
        assertThat(CompanionSettingsSupport.serviceOrigin("https://ai.example.org/ai")).isEmpty();
        assertThat(CompanionSettingsSupport.serviceOrigin("https://ai.example.org?a=1")).isEmpty();
        assertThat(CompanionSettingsSupport.serviceOrigin("https://ai.example.org#frag")).isEmpty();
        assertThat(CompanionSettingsSupport.serviceOrigin("ftp://ai.example.org")).isEmpty();
    }

    @Test
    void adminTransportAllowsHttpsNumericLoopbackOrExplicitInsecureOptIn() {
        assertThat(CompanionSettingsSupport.adminTransportAllowed("https://ai.example.org", false)).isTrue();
        assertThat(CompanionSettingsSupport.adminTransportAllowed("http://127.0.0.1:8090", false)).isTrue();
        assertThat(CompanionSettingsSupport.adminTransportAllowed("http://[::1]:8090", false)).isTrue();
        assertThat(CompanionSettingsSupport.adminTransportAllowed("http://ai.example.org", false)).isFalse();
        assertThat(CompanionSettingsSupport.adminTransportAllowed("http://localhost", false)).isFalse();
        assertThat(CompanionSettingsSupport.adminTransportAllowed("http://127.0.0.1.example.org", false)).isFalse();
        assertThat(CompanionSettingsSupport.adminTransportAllowed("http://ai.example.org", true)).isTrue();
        assertThat(CompanionSettingsSupport.adminTransportAllowed("http://ai.example.org/path", true)).isFalse();
    }

    @Test
    void adminConditionAndPropertiesBothEnforceTransportPolicy() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("oiep.companion.enabled", "true")
                .withProperty("oiep.companion.admin-helper.enabled", "true")
                .withProperty("oiep.companion.admin-helper.token", "a".repeat(32))
                .withProperty("oiep.companion.base-url", "http://ai:8090");
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        CompanionConditions.AdminHelper condition = new CompanionConditions.AdminHelper();
        CompanionProperties properties = new CompanionProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://ai:8090");
        properties.getAdminHelper().setEnabled(true);
        properties.getAdminHelper().setToken("a".repeat(32));

        assertThat(condition.matches(context, null)).isFalse();
        assertThat(properties.adminHelperUsable()).isFalse();

        environment.setProperty("oiep.companion.admin-helper.allow-insecure-http", "true");
        properties.getAdminHelper().setAllowInsecureHttp(true);
        assertThat(condition.matches(context, null)).isTrue();
        assertThat(properties.adminHelperUsable()).isTrue();

        environment.setProperty("oiep.companion.admin-helper.allow-insecure-http", "false");
        environment.setProperty("oiep.companion.base-url", "https://ai.example.org");
        properties.getAdminHelper().setAllowInsecureHttp(false);
        properties.setBaseUrl("https://ai.example.org");
        assertThat(condition.matches(context, null)).isTrue();
        assertThat(properties.adminHelperUsable()).isTrue();
    }

    @Test
    void environmentOptInIsBoundAndControlsAdminBeanRegistration() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(AdminTransportConfiguration.class)
                .withPropertyValues(
                        "OIEP_COMPANION_ENABLED=true",
                        "OIEP_COMPANION_ADMIN_HELPER_ENABLED=true",
                        "OIEP_COMPANION_ADMIN_TOKEN=" + "a".repeat(32),
                        "OIEP_COMPANION_BASE_URL=http://ai:8090");

        runner.run(context -> {
            assertThat(context).doesNotHaveBean("adminTransportMarker");
            assertThat(context.getBean(CompanionProperties.class).adminHelperUsable()).isFalse();
        });
        runner.withPropertyValues("OIEP_COMPANION_ADMIN_ALLOW_INSECURE_HTTP=true").run(context -> {
            assertThat(context).hasBean("adminTransportMarker");
            assertThat(context.getBean(CompanionProperties.class).adminHelperUsable()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CompanionProperties.class)
    @PropertySource("classpath:application.properties")
    static class AdminTransportConfiguration {
        @Bean
        @Conditional(CompanionConditions.AdminHelper.class)
        String adminTransportMarker() {
            return "enabled";
        }
    }

    @Test
    void sameOriginComparesOnlyTheOrigin() {
        assertThat(CompanionSettingsSupport.sameOrigin(
                "https://ai.example.org/ui/", "https://ai.example.org")).isTrue();
        assertThat(CompanionSettingsSupport.sameOrigin(
                "https://ai.example.org/ui/", "https://ai2.example.org")).isFalse();
        assertThat(CompanionSettingsSupport.sameOrigin("", "https://ai.example.org")).isFalse();
    }

    @Test
    void secretRulesRejectBlankPlaceholdersAndShortValues() {
        assertThat(CompanionSettingsSupport.secretProblem("")).isEqualTo("not set");
        assertThat(CompanionSettingsSupport.secretProblem("   ")).isEqualTo("not set");
        assertThat(CompanionSettingsSupport.secretProblem("<paste-your-own-long-random-secret>"))
                .contains("placeholder");
        assertThat(CompanionSettingsSupport.secretProblem("REPLACE-WITH-THE-OPERATOR-TOKEN"))
                .contains("placeholder");
        assertThat(CompanionSettingsSupport.secretProblem("short-token"))
                .contains("shorter than");
        assertThat(CompanionSettingsSupport.secretProblem("x".repeat(513)))
                .contains("longer than");

        assertThat(CompanionSettingsSupport.isUsableSecret("a".repeat(16))).isTrue();
        assertThat(CompanionSettingsSupport.isUsableSecret("a".repeat(32))).isTrue();
        assertThat(CompanionSettingsSupport.isUsableSecret("a".repeat(15))).isFalse();
    }
}
