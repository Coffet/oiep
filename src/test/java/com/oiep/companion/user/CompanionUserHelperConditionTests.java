package com.oiep.companion.user;

import com.oiep.companion.CompanionConditions;
import com.oiep.companion.CompanionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-closed coverage for the condition that decides whether the user helper may exist.
 * The same rules back the {@link CompanionUserHelperExtension}, the model advice and the
 * CSP contributor, so an unusable widget URL disables all three.
 */
class CompanionUserHelperConditionTests {

    @Test
    void disabledMasterSwitchMeansNoHelperEvenWithAValidWidgetUrl() {
        assertThat(matches("oiep.companion.enabled", "false",
                "oiep.companion.user-helper.widget-url", "https://ai.example.org/ui/"))
                .isFalse();
    }

    @Test
    void missingConfigurationIsOffByDefault() {
        assertThat(matches()).isFalse();
        assertThat(matches("oiep.companion.enabled", "true")).isFalse();
    }

    @Test
    void validExplicitWidgetUrlEnablesTheHelper() {
        assertThat(matches("oiep.companion.enabled", "true",
                "oiep.companion.user-helper.widget-url", "https://ai.example.org/ui/"))
                .isTrue();
        assertThat(matches("oiep.companion.enabled", "true",
                "oiep.companion.user-helper.widget-url", "http://127.0.0.1:8090/ui/"))
                .isTrue();
    }

    @Test
    void widgetUrlDerivedFromTheServiceBaseUrlEnablesTheHelper() {
        assertThat(matches("oiep.companion.enabled", "true",
                "oiep.companion.base-url", "https://ai.example.org"))
                .isTrue();
    }

    @Test
    void userHelperSubFlagFalseDisablesTheHelper() {
        assertThat(matches("oiep.companion.enabled", "true",
                "oiep.companion.user-helper.enabled", "false",
                "oiep.companion.user-helper.widget-url", "https://ai.example.org/ui/"))
                .isFalse();
    }

    @Test
    void unsafeOrUnparsableWidgetUrlsFailClosed() {
        String[] invalidUrls = {
                "javascript:alert(document.domain)",
                "data:text/html,<script>alert(1)</script>",
                "//ai.example.org/ui/",
                "/ui/",
                "ai.example.org/ui/",
                "https://user:password@ai.example.org/ui/",
                "ftp://ai.example.org/ui/",
                "not a url"
        };
        for (String widgetUrl : invalidUrls) {
            assertThat(matches("oiep.companion.enabled", "true",
                    "oiep.companion.user-helper.widget-url", widgetUrl))
                    .as("widget URL must fail closed: %s", widgetUrl)
                    .isFalse();
        }
    }

    @Test
    void propertiesExposeTheValidatedWidgetUrlAndOrigin() {
        CompanionProperties properties = new CompanionProperties();
        properties.setEnabled(true);
        properties.getUserHelper().setWidgetUrl("https://ai.example.org/ui/");

        assertThat(properties.userHelperUsable()).isTrue();
        assertThat(properties.effectiveWidgetUrl()).isEqualTo("https://ai.example.org/ui/");
        assertThat(properties.widgetOrigin()).isEqualTo("https://ai.example.org");
    }

    @Test
    void propertiesDeriveTheWidgetUrlFromTheBaseUrl() {
        CompanionProperties properties = new CompanionProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://ai.example.org");

        assertThat(properties.userHelperUsable()).isTrue();
        assertThat(properties.effectiveWidgetUrl()).isEqualTo("https://ai.example.org/ui/");
        assertThat(properties.widgetOrigin()).isEqualTo("https://ai.example.org");
    }

    @Test
    void propertiesFailClosedForAnUnsafeWidgetUrl() {
        CompanionProperties properties = new CompanionProperties();
        properties.setEnabled(true);
        properties.getUserHelper().setWidgetUrl("javascript:alert(1)");

        assertThat(properties.userHelperUsable()).isFalse();
        assertThat(properties.widgetOrigin()).isEmpty();
    }

    @Test
    void conditionReadsPropertiesFromTheEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("oiep.companion.enabled", "true");
        environment.setProperty("oiep.companion.user-helper.widget-url", "https://ai.example.org/ui/");

        assertThat(new CompanionConditions.UserHelper().matches(contextFor(environment), null))
                .isTrue();
    }

    private static boolean matches(String... keyValuePairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int index = 0; index + 1 < keyValuePairs.length; index += 2) {
            environment.setProperty(keyValuePairs[index], keyValuePairs[index + 1]);
        }
        return new CompanionConditions.UserHelper().matches(contextFor(environment), null);
    }

    private static ConditionContext contextFor(Environment environment) {
        return new ConditionContext() {
            @Override
            public BeanDefinitionRegistry getRegistry() {
                return null;
            }

            @Override
            public ConfigurableListableBeanFactory getBeanFactory() {
                return null;
            }

            @Override
            public Environment getEnvironment() {
                return environment;
            }

            @Override
            public ResourceLoader getResourceLoader() {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return null;
            }
        };
    }
}
