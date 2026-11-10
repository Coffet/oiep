package com.oiep.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin {@code returnTo} parameter is attacker-controllable. It must never produce an
 * open redirect, a header-injection primitive or the Tomcat dot-segment 500.
 */
class SafeAdminReturnTests {

    @Test
    void normalAdminTargetsArePreserved() {
        assertThat(AdminController.safeAdminReturn("/admin")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/admin/reports/12")).isEqualTo("/admin/reports/12");
        assertThat(AdminController.safeAdminReturn("/admin?q=acme&status=hidden"))
                .isEqualTo("/admin?q=acme&status=hidden");
        assertThat(AdminController.safeAdminReturn("  /admin/reports/3  ")).isEqualTo("/admin/reports/3");
    }

    @Test
    void nonAdminAndProtocolRelativeTargetsFallBack() {
        assertThat(AdminController.safeAdminReturn(null)).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("https://evil.example/admin")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("//evil.example/admin")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/\\evil.example/admin")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/administrator")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/reports/1")).isEqualTo("/admin");
    }

    @Test
    void dotSegmentsAndControlCharactersFallBack() {
        assertThat(AdminController.safeAdminReturn("/admin/../evil.example")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/admin/../../evil.example")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/admin/./reports")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/admin\r\nX-Injected: 1")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/admin\nSet-Cookie: pwned=1")).isEqualTo("/admin");
        assertThat(AdminController.safeAdminReturn("/admin\u0000")).isEqualTo("/admin");
    }

    @Test
    void oversizedTargetsFallBack() {
        assertThat(AdminController.safeAdminReturn("/admin?q=" + "a".repeat(5000))).isEqualTo("/admin");
        String withinLimit = "/admin?q=" + "a".repeat(1900);
        assertThat(AdminController.safeAdminReturn(withinLimit)).isEqualTo(withinLimit);
    }
}
