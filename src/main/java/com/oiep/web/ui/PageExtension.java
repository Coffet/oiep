package com.oiep.web.ui;

/**
 * Optional UI extension point: a view fragment that OIEP pages include when a component is
 * enabled.
 *
 * <p>Core pages contain small generic loops over the {@code pageExtensions} model attribute
 * (see {@code GlobalModelAdvice}) and never mention a specific component. A component
 * registers one or more {@code PageExtension} beans; when the component is disabled, removed
 * or fails its configuration check, no bean exists, the loops render nothing, and the pages
 * are exactly as they were before the component was added.</p>
 *
 * <p>Fragments are JSP files under {@code /WEB-INF/jsp/...} and are included at request time
 * with {@code <jsp:include>}; each fragment must therefore be a valid JSP on its own (its own
 * taglib include, no reliance on the including page's directives) and must render a
 * self-contained block of markup: assets it needs are linked from inside the fragment.</p>
 */
public interface PageExtension {

    /**
     * The page slot this extension fills. OIEP currently defines:
     * <ul>
     *   <li>{@code "index"} — after the public debrief form on the landing page</li>
     *   <li>{@code "admin-nav"} — extra operator links in the admin navigation</li>
     * </ul>
     */
    String page();

    /**
     * Absolute web-app path of the JSP fragment, e.g.
     * {@code "/WEB-INF/jsp/companion/user-helper.jsp"}.
     */
    String fragment();

    /**
     * JavaBean bridge for JSP EL: the generic include loops use {@code ${extension.page}} and
     * {@code ${extension.fragment}}. EL resolves only JavaBean getters, not the interface
     * methods above, so these defaults keep the SPI readable while remaining EL-friendly.
     */
    default String getPage() {
        return page();
    }

    /** JavaBean bridge for JSP EL; see {@link #getPage()}. */
    default String getFragment() {
        return fragment();
    }
}
