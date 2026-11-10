package com.oiep.companion;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Conditions for the optional AI companion component.
 *
 * <p>Every bean in the component is conditional. The master switch is
 * {@code oiep.companion.enabled}; each sub-feature additionally requires its own switch and a
 * usable configuration. A misconfigured sub-feature therefore registers no beans at all
 * (fail closed): no routes, no page extension, no CSP directive. Values are read from the
 * {@link org.springframework.core.env.Environment} directly so the conditions can be used on
 * any bean without relying on property-binding order.</p>
 */
public final class CompanionConditions {

    static final String MASTER_PROPERTY = "oiep.companion.enabled";

    private CompanionConditions() {
    }

    /** True when the component is enabled and the companion service base URL is valid. */
    public static class Enabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isEnabled(context);
        }
    }

    /** True when the public user helper can be embedded (valid widget URL). */
    public static class UserHelper implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (!isEnabled(context)) {
                return false;
            }
            String enabled = property(context, "oiep.companion.user-helper.enabled", "true");
            if (!Boolean.parseBoolean(enabled)) {
                return false;
            }
            String widgetUrl = CompanionSettingsSupport.effectiveWidgetUrl(
                    property(context, "oiep.companion.user-helper.widget-url", ""),
                    property(context, "oiep.companion.base-url", ""));
            return !CompanionSettingsSupport.originOf(widgetUrl).isEmpty();
        }
    }

    /** True when the operator-only admin helper is enabled and fully configured. */
    public static class AdminHelper implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (!isEnabled(context)) {
                return false;
            }
            if (!Boolean.parseBoolean(property(context, "oiep.companion.admin-helper.enabled", "false"))) {
                return false;
            }
            String origin = CompanionSettingsSupport.serviceOrigin(
                    property(context, "oiep.companion.base-url", ""));
            if (origin.isEmpty()) {
                return false;
            }
            return CompanionSettingsSupport.isUsableSecret(
                    property(context, "oiep.companion.admin-helper.token", ""));
        }
    }

    /** True when the read-only experiences API is enabled with a usable token. */
    public static class ReadApi implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (!isEnabled(context)) {
                return false;
            }
            if (!Boolean.parseBoolean(property(context, "oiep.companion.read-api.enabled", "false"))) {
                return false;
            }
            return CompanionSettingsSupport.isUsableSecret(
                    property(context, "oiep.companion.read-api.token", ""));
        }
    }

    static boolean isEnabled(ConditionContext context) {
        try {
            return Boolean.parseBoolean(property(context, MASTER_PROPERTY, "false"));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String property(ConditionContext context, String key, String fallback) {
        if (context.getEnvironment() == null) {
            return fallback;
        }
        String value = context.getEnvironment().getProperty(key);
        return value == null ? fallback : value.trim();
    }
}
