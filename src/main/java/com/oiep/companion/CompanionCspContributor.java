package com.oiep.companion;

import com.oiep.config.CspContributor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adds the {@code frame-src} directive needed to embed the companion chat widget.
 *
 * <p>Only the exact configured widget origin is allowed (never a wildcard, never the whole
 * scheme). When the user helper is disabled or its URL is invalid this bean does not exist,
 * so the shipped CSP is unchanged.</p>
 */
@Component
@Conditional(CompanionConditions.UserHelper.class)
public class CompanionCspContributor implements CspContributor {

    private final String widgetOrigin;

    public CompanionCspContributor(CompanionProperties properties) {
        this.widgetOrigin = properties.widgetOrigin();
    }

    @Override
    public List<String> directives() {
        if (widgetOrigin.isEmpty()) {
            return List.of();
        }
        return List.of("frame-src 'self' " + widgetOrigin);
    }
}
