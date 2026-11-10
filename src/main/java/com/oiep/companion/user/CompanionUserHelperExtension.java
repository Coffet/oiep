package com.oiep.companion.user;

import com.oiep.companion.CompanionConditions;
import com.oiep.companion.CompanionProperties;
import com.oiep.web.ui.PageExtension;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Optional component: embeds the companion service's chat widget on the public debrief form
 * page so visitors can draft their answers in their own words.
 *
 * <p>The bean only exists when the companion is enabled and the configured widget URL is a
 * valid http(s) URL ({@link CompanionConditions.UserHelper}). A misconfigured URL therefore
 * registers no extension, no view model and no CSP {@code frame-src} directive: the page is
 * byte-for-byte the shipped OIEP page (fail closed).</p>
 *
 * <p>Nothing here talks to the companion service: the browser loads the widget from its own
 * origin, and the page's script only receives the visitor's structured draft through an
 * origin-checked {@code postMessage} hand-off. No token or secret ever reaches the browser.</p>
 */
@Component
@Conditional(CompanionConditions.UserHelper.class)
public class CompanionUserHelperExtension implements PageExtension {

    /** Landing-page slot, rendered after the debrief form by {@code index.jsp}. */
    static final String PAGE = "index";

    /** Self-contained fragment; it links the stylesheet and script it needs itself. */
    static final String FRAGMENT = "/WEB-INF/jsp/companion/user-helper.jsp";

    private final CompanionProperties properties;

    public CompanionUserHelperExtension(CompanionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String page() {
        return PAGE;
    }

    @Override
    public String fragment() {
        return FRAGMENT;
    }

    /**
     * Full URL of the chat widget to embed, already validated by the condition. The fragment
     * escapes it when rendering; the browser loads it directly from the companion origin.
     */
    public String getWidgetUrl() {
        return properties.effectiveWidgetUrl();
    }

    /** Origin of {@link #getWidgetUrl()}, also used by the CSP {@code frame-src} contributor. */
    public String getWidgetOrigin() {
        return properties.widgetOrigin();
    }
}
