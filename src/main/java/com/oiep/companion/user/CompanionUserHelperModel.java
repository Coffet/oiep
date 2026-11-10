package com.oiep.companion.user;

/**
 * Model exposed to the user-helper fragment: the validated widget URL and its origin.
 *
 * <p>The JSP renders both values with XML escaping; the script uses the origin to validate
 * {@code postMessage} events. There is deliberately no token, no service URL and no other
 * configuration in this object — the browser must never learn anything about the companion
 * beyond the public widget URL.</p>
 */
public final class CompanionUserHelperModel {

    private final String widgetUrl;
    private final String widgetOrigin;

    public CompanionUserHelperModel(String widgetUrl, String widgetOrigin) {
        this.widgetUrl = widgetUrl == null ? "" : widgetUrl;
        this.widgetOrigin = widgetOrigin == null ? "" : widgetOrigin;
    }

    public String getWidgetUrl() {
        return widgetUrl;
    }

    public String getWidgetOrigin() {
        return widgetOrigin;
    }

    /** True when both values are usable; the fragment is rendered only in that case. */
    public boolean isAvailable() {
        return !widgetUrl.isEmpty() && !widgetOrigin.isEmpty();
    }

    @Override
    public String toString() {
        // Safe to log: the widget URL is public configuration, never a secret.
        return "CompanionUserHelperModel[widgetUrl=" + widgetUrl + ", widgetOrigin=" + widgetOrigin + "]";
    }
}
