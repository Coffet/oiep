package com.oiep.companion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
