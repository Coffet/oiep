package com.oiep.companion.admin;

import com.oiep.companion.CompanionConditions;
import com.oiep.web.ui.PageExtension;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Adds the "AI helper" link to the admin navigation while the admin helper is fully configured.
 *
 * <p>The core admin top bar contains one generic loop over {@code PageExtension} beans with
 * {@code page() == "admin-nav"}; it never mentions this component. When the component is
 * disabled or misconfigured this bean does not exist, the loop renders nothing, and the admin
 * navigation is exactly as it was before the component was added.</p>
 */
@Component
@Conditional(CompanionConditions.AdminHelper.class)
public class AdminAssistantNavExtension implements PageExtension {

    /** The page slot the core admin top bar renders (see {@link PageExtension}). */
    static final String PAGE_SLOT = "admin-nav";

    /** Absolute web-app path of the operator-only navigation fragment. */
    static final String FRAGMENT = "/WEB-INF/jsp/companion/admin-nav.jsp";

    @Override
    public String page() {
        return PAGE_SLOT;
    }

    @Override
    public String fragment() {
        return FRAGMENT;
    }
}
