package com.oiep.companion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the optional AI companion component ({@code oiep.companion.*}).
 *
 * <p>The whole component is inert unless {@code oiep.companion.enabled} is true (environment
 * variable {@code OIEP_COMPANION_ENABLED}). Sub-features then fail closed on their own: the
 * user helper needs a valid widget URL, the admin helper needs a service base URL plus a
 * usable operator token, and the read API needs a usable read-only token. The bootstrap
 * runner logs which sub-features are active and why the others are not (never a secret).</p>
 *
 * <p>See {@code docs/COMPANION.md} for the operator-facing configuration reference.</p>
 */
@Component
@ConfigurationProperties(prefix = "oiep.companion")
public class CompanionProperties {

    /** Master switch for the optional AI companion component. */
    private boolean enabled;

    /** Companion service origin, e.g. {@code http://127.0.0.1:8090} or {@code https://ai.oiep.example}. */
    private String baseUrl = "";

    private UserHelper userHelper = new UserHelper();
    private AdminHelper adminHelper = new AdminHelper();
    private ReadApi readApi = new ReadApi();
    private Http http = new Http();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public UserHelper getUserHelper() {
        return userHelper;
    }

    public void setUserHelper(UserHelper userHelper) {
        this.userHelper = userHelper == null ? new UserHelper() : userHelper;
    }

    public AdminHelper getAdminHelper() {
        return adminHelper;
    }

    public void setAdminHelper(AdminHelper adminHelper) {
        this.adminHelper = adminHelper == null ? new AdminHelper() : adminHelper;
    }

    public ReadApi getReadApi() {
        return readApi;
    }

    public void setReadApi(ReadApi readApi) {
        this.readApi = readApi == null ? new ReadApi() : readApi;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http == null ? new Http() : http;
    }

    /* -- derived, fail-closed views (no side effects) ---------------------- */

    /** Widget URL to embed, or "" when the user helper cannot be shown. */
    public String effectiveWidgetUrl() {
        return CompanionSettingsSupport.effectiveWidgetUrl(
                userHelper.getWidgetUrl(), baseUrl);
    }

    /** Origin to allow in the CSP {@code frame-src}, or "" when unusable. */
    public String widgetOrigin() {
        return CompanionSettingsSupport.originOf(effectiveWidgetUrl());
    }

    /** Validated companion service origin, or "" when unusable. */
    public String serviceOrigin() {
        return CompanionSettingsSupport.serviceOrigin(baseUrl);
    }

    public boolean userHelperUsable() {
        return enabled && userHelper.isEnabled() && !widgetOrigin().isEmpty();
    }

    public boolean adminHelperUsable() {
        return enabled
                && adminHelper.isEnabled()
                && !serviceOrigin().isEmpty()
                && CompanionSettingsSupport.isUsableSecret(adminHelper.getToken());
    }

    public boolean readApiUsable() {
        return enabled && readApi.isEnabled() && CompanionSettingsSupport.isUsableSecret(readApi.getToken());
    }

    /** User-helper sub-feature. */
    public static class UserHelper {

        /** Show the user helper on the public form page. */
        private boolean enabled = true;

        /**
         * Full URL of the companion chat widget (normally {@code <base-url>/ui/}). When
         * empty, {@code <base-url>/ui/} is used.
         */
        private String widgetUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWidgetUrl() {
            return widgetUrl;
        }

        public void setWidgetUrl(String widgetUrl) {
            this.widgetUrl = widgetUrl == null ? "" : widgetUrl;
        }
    }

    /** Admin-helper sub-feature. */
    public static class AdminHelper {

        /** Expose the operator-only AI helper pages under /admin/assistant. */
        private boolean enabled = false;

        /** Companion admin bearer token. Server-side only; never sent to the browser. */
        private String token = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token == null ? "" : token;
        }
    }

    /** Read-only experiences API consumed by the companion's corpus sync. */
    public static class ReadApi {

        /** Expose {@code GET /api/v1/experiences} for the companion service. */
        private boolean enabled = false;

        /** Bearer token the companion service presents. Read-only; no other scope. */
        private String token = "";

        /** Maximum page size (1..500); the service default. */
        private int pageSize = 200;

        /**
         * Difficulty value emitted for {@code difficulty-mode=constant} (the default),
         * because the OIEP form does not collect interview difficulty.
         */
        private String difficulty = "MODERATE";

        /**
         * {@code constant} (default) emits {@link #difficulty} for every item;
         * {@code vibe-proxy} derives a coarse value from the interviewer-vibe field.
         */
        private String difficultyMode = "constant";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token == null ? "" : token;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty == null ? "MODERATE" : difficulty;
        }

        public String getDifficultyMode() {
            return difficultyMode;
        }

        public void setDifficultyMode(String difficultyMode) {
            this.difficultyMode = difficultyMode == null ? "constant" : difficultyMode;
        }
    }

    /** HTTP timeouts used by the server-side proxy (milliseconds). */
    public static class Http {

        private int connectTimeoutMs = 3000;
        private int requestTimeoutMs = 15000;

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }
}
