package com.oiep.companion;

import com.oiep.config.CspContributor;
import com.oiep.config.WebConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CSP extension point must never weaken the shipped policy: it only appends directives,
 * and an empty contributor list produces exactly the original header value.
 */
class CompanionCspIntegrationTests {

    private static final String WIDGET_ORIGIN = "https://ai.example.org";

    @Test
    void contributorAppendsOnlyTheFrameSourceDirective() {
        CompanionProperties properties = new CompanionProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(WIDGET_ORIGIN);
        CompanionCspContributor contributor = new CompanionCspContributor(properties);

        assertThat(contributor.directives()).containsExactly("frame-src 'self' " + WIDGET_ORIGIN);
    }

    @Test
    void contributorWithoutAValidWidgetUrlAddsNothing() {
        CompanionProperties properties = new CompanionProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("javascript:alert(1)");
        CompanionCspContributor contributor = new CompanionCspContributor(properties);

        assertThat(contributor.directives()).isEmpty();
    }

    @Test
    void webConfigAppendsContributorDirectivesToTheBasePolicy() {
        CspContributor contributor = () -> List.of("frame-src 'self' " + WIDGET_ORIGIN);
        String policy = WebConfig.contentSecurityPolicy(List.of(contributor));

        assertThat(policy).startsWith(WebConfig.contentSecurityPolicy());
        assertThat(policy).contains("; frame-src 'self' " + WIDGET_ORIGIN);
        assertThat(policy).doesNotContain("unsafe-inline");
        // Default-src and the inline hashes are untouched.
        assertThat(policy).contains("default-src 'self'");
        assertThat(policy).contains("frame-ancestors 'none'");
        assertThat(policy).contains("connect-src 'self'");
    }

    @Test
    void emptyContributorsProduceTheExactOriginalPolicy() {
        assertThat(WebConfig.contentSecurityPolicy(List.of()))
                .isEqualTo(WebConfig.contentSecurityPolicy());
        assertThat(WebConfig.contentSecurityPolicy(null))
                .isEqualTo(WebConfig.contentSecurityPolicy());
        assertThat(WebConfig.contentSecurityPolicy()).doesNotContain("frame-src");
    }

    @Test
    void malformedContributorDirectivesAreIgnoredNotTrusted() {
        CspContributor blank = () -> List.of(" ", "");
        CspContributor nullDirectives = () -> null;
        CspContributor nullListElement = () -> java.util.Arrays.asList("frame-src 'self'", null);

        String policy = WebConfig.contentSecurityPolicy(
                List.of(blank, nullDirectives, nullListElement));
        assertThat(policy).isEqualTo(WebConfig.contentSecurityPolicy() + "; frame-src 'self'");
    }
}
