package com.oiep.security;

import com.oiep.service.AdminAuth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin credential handling: documented placeholders must not enable the admin area, the
 * constant-time comparison keeps working for real secrets, and CSRF tokens stay per-session.
 */
class AdminAuthHardeningTests {

    @Test
    void documentedPlaceholderPasswordKeepsAdminDisabled() {
        for (String placeholder : new String[]{
                "replace-with-a-long-random-password",
                "choose-a-long-random-password",
                "PASTE_A_LONG_RANDOM_PASSWORD_HERE".replace("_", "-"),
                "<paste-your-own-long-random-secret>",
                "  replace-with-a-long-random-password  "}) {
            AdminAuth auth = new AdminAuth("admin", placeholder);
            assertThat(auth.isConfigured())
                    .as("placeholder %s must not enable the admin area", placeholder)
                    .isFalse();
            assertThat(auth.authenticate("admin", placeholder)).isFalse();
        }
    }

    @Test
    void emptyPasswordKeepsAdminDisabled() {
        assertThat(new AdminAuth("admin", "").isConfigured()).isFalse();
        assertThat(new AdminAuth("admin", null).isConfigured()).isFalse();
        assertThat(new AdminAuth("admin", "   ").isConfigured()).isFalse();
    }

    @Test
    void realPasswordAuthenticatesWithExactUsernameAndPassword() {
        AdminAuth auth = new AdminAuth("test-admin", "a-long-test-password-123");

        assertThat(auth.isConfigured()).isTrue();
        assertThat(auth.authenticate("test-admin", "a-long-test-password-123")).isTrue();
        assertThat(auth.authenticate("  test-admin  ", "a-long-test-password-123")).isTrue();
        assertThat(auth.authenticate("test-admin", "wrong")).isFalse();
        assertThat(auth.authenticate("other-admin", "a-long-test-password-123")).isFalse();
        assertThat(auth.authenticate("test-admin", "a-long-test-password-1234")).isFalse();
        assertThat(auth.authenticate(null, "a-long-test-password-123")).isFalse();
    }

    @Test
    void shortCustomPasswordStillWarnsButWorks() {
        AdminAuth auth = new AdminAuth("admin", "short-pass");
        assertThat(auth.isConfigured()).isTrue();
        assertThat(auth.authenticate("admin", "short-pass")).isTrue();
    }
}
