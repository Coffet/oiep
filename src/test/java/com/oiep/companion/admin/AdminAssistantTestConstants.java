package com.oiep.companion.admin;

/**
 * Shared constants for the operator AI helper tests.
 *
 * <p>The token is deliberately long enough to satisfy the companion component's secret checks
 * ({@code CompanionSettingsSupport.isUsableSecret}): a too-short placeholder would disable the
 * admin helper entirely and every proxy test would fail for the wrong reason.</p>
 */
final class AdminAssistantTestConstants {

    static final String ADMIN_USER = "test-admin";
    static final String ADMIN_PASSWORD = "test-password";

    /** 45 characters: above the 32-character minimum the companion documents for admin tokens. */
    static final String ADMIN_TOKEN = "test-admin-token-0123456789abcdef0123456789";

    private AdminAssistantTestConstants() {
    }
}
