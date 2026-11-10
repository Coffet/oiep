package com.oiep.companion.user;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static contract checks for the files that travel with the user helper. These complement
 * the rendered-HTML end-to-end test: they pin the security rules that must hold even if the
 * URL configuration changes (no inline script/style, no inline handlers, escaped attributes,
 * origin-checked messaging, never-submit mapping, CSS accessibility/design constraints).
 */
class CompanionUserHelperFragmentContractTests {

    private static final Path JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/companion/user-helper.jsp");
    private static final Path SCRIPT = Path.of("src/main/resources/static/js/companion.js");
    private static final Path STYLE = Path.of("src/main/resources/static/css/companion.css");

    private static final List<String> MESSAGE_KEYS = List.of(
            "companion.helper.kicker",
            "companion.helper.title",
            "companion.helper.lead",
            "companion.helper.privacy",
            "companion.helper.optional",
            "companion.helper.iframeTitle",
            "companion.helper.loadingHint",
            "companion.helper.apply",
            "companion.helper.prefillNote",
            "companion.helper.fallback",
            "companion.helper.checking",
            "companion.helper.applied",
            "companion.helper.appliedCount",
            "companion.helper.unchanged",
            "companion.helper.noMatch",
            "companion.helper.skipped",
            "companion.helper.notReady",
            "companion.helper.unavailable");

    @Test
    void fragmentIsASelfContainedJspBlock() throws IOException {
        String jsp = read(JSP);

        assertThat(jsp).contains("<%@ include file=\"../fragments/taglibs.jspf\" %>");
        assertThat(jsp).contains("<c:set var=\"ctx\" value=\"${pageContext.request.contextPath}\"/>");
        assertThat(jsp).contains("data-companion-helper");
        assertThat(jsp).contains("<iframe");
        assertThat(jsp).contains("data-companion-widget");
        assertThat(jsp).contains("data-companion-apply");
        assertThat(jsp).contains("data-companion-status");
        assertThat(jsp).contains("data-companion-fallback");
        // Its own assets, linked from the fragment (PageExtension contract).
        assertThat(jsp).contains("/css/companion.css");
        assertThat(jsp).contains("/js/companion.js");
        assertThat(jsp).contains("defer");
    }

    @Test
    void iframeAttributesAreLockedDown() throws IOException {
        String jsp = read(JSP);

        assertThat(jsp).contains("src=\"${fn:escapeXml(helper.widgetUrl)}\"");
        assertThat(jsp).contains("loading=\"lazy\"");
        assertThat(jsp).contains("referrerpolicy=\"no-referrer\"");
        assertThat(jsp).contains("sandbox=\"allow-scripts allow-forms allow-same-origin\"");
        assertThat(jsp).contains("allow=\"clipboard-write\"");
        assertThat(jsp).contains("title=\"${fn:escapeXml(msgIframeTitle)}\"");
        // The widget URL comes from validated server configuration, never hard-coded in the
        // JSP and never built in the browser.
        assertThat(jsp).doesNotContain("http://", "https://");
        assertThat(jsp).doesNotContain("src=\"${helper.widgetUrl}\"");
    }

    @Test
    void fragmentGuardFailsClosedAndEveryVisibleStringComesFromTheBundle() throws IOException {
        String jsp = read(JSP);

        assertThat(jsp).contains("<c:if test=\"${not empty helper and helper.available}\">");
        for (String key : MESSAGE_KEYS) {
            assertThat(jsp)
                    .as("message key %s must be used so EN and zh-HK both render", key)
                    .contains("code=\"" + key + "\"");
        }
    }

    @Test
    void fragmentHasNoInlineScriptStyleOrEventHandlers() throws IOException {
        String jsp = read(JSP);

        // Any script tag must be an external, CSP-allowed file.
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\bsrc=)", Pattern.CASE_INSENSITIVE);
        assertThat(inlineScript.matcher(jsp).find()).as("no inline <script> allowed").isFalse();
        assertThat(Pattern.compile("<style", Pattern.CASE_INSENSITIVE).matcher(jsp).find())
                .as("no inline <style> allowed")
                .isFalse();
        assertThat(Pattern.compile("\\sstyle\\s*=", Pattern.CASE_INSENSITIVE).matcher(jsp).find())
                .as("no inline style attributes allowed")
                .isFalse();
        assertThat(Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE).matcher(jsp).find())
                .as("no inline event handlers allowed")
                .isFalse();
        assertThat(jsp).doesNotContain("javascript:");
    }

    @Test
    void fragmentAnnouncesStatusPolitely() throws IOException {
        String jsp = read(JSP);

        assertThat(jsp).contains("role=\"status\"");
        assertThat(jsp).contains("aria-live=\"polite\"");
        assertThat(jsp).contains("aria-labelledby=\"companion-helper-heading\"");
        assertThat(jsp).contains("id=\"companion-helper-heading\"");
        assertThat(jsp).contains("aria-describedby=\"companion-helper-note\"");
        assertThat(jsp).contains("aria-describedby=\"companion-helper-hint\"");
    }

    @Test
    void scriptOnlyFillsTheFormThroughAnOriginCheckedBridge() throws IOException {
        String script = read(SCRIPT);

        // Origin + window identity validation.
        assertThat(script).contains("event.origin !== widgetOrigin");
        assertThat(script).contains("event.source !== iframe.contentWindow");
        assertThat(script).contains("data-widget-origin");
        // Exact documented message contract.
        assertThat(script).contains("oiep-ai:request-prefill");
        assertThat(script).contains("oiep-ai:prefill");
        assertThat(script).contains("oiep-ai:prefill-unavailable");
        // Never submit and never write raw HTML.
        assertThat(script).doesNotContain(".submit(", "requestSubmit", "form.submit");
        assertThat(script).doesNotContain("innerHTML", "document.write", "eval(");
        // Only empty fields are filled; answered fields are preserved.
        assertThat(script).contains("input.value.trim() !== \"\"");
        assertThat(script).contains("if (inputs.some((input) => input.checked)) return;");
        // Duplicate messages are guarded.
        assertThat(script).contains("lastAppliedSignature");
        // Counts are rendered through a {0} template from the message bundle.
        assertThat(script).contains("\"{0}\"");
        assertThat(script).contains("replace(\"{0}\"");
    }

    @Test
    void scriptMapsTheCompanionContractOntoOiepFieldValues() throws IOException {
        String script = read(SCRIPT);

        assertThat(script).contains("PREFILL MAPPING");
        assertThat(script).contains("#debrief-form", "#company", "#role", "#location", "#story");
        assertThat(script).contains("work_mode", "outcome", "rounds");
        // Work modes.
        assertThat(script).contains("ONSITE: \"onsite\"", "HYBRID: \"hybrid\"", "REMOTE: \"remote\"");
        // Outcomes.
        assertThat(script).contains("OFFER: \"offer\"", "REJECTED: \"rejection\"",
                "WITHDRAWN: \"withdrew\"", "PENDING: \"waiting\"", "NO_RESPONSE: \"ghosted\"");
        // Round types (SCREEN/PHONE -> phone, TAKE_HOME -> takehome, ONSITE/HR/OTHER -> other).
        assertThat(script).contains("SCREEN: \"phone\"", "PHONE: \"phone\"", "TECHNICAL: \"technical\"",
                "TAKE_HOME: \"takehome\"", "SYSTEM_DESIGN: \"systemdesign\"",
                "BEHAVIORAL: \"behavioral\"", "ONSITE: \"other\"", "HR: \"other\"", "OTHER: \"other\"");
        // Round topics map onto the questionTypes checkboxes in one block.
        assertThat(script).contains("checkboxGroup: \"questionTypes\"");
        assertThat(script).contains("topicKeywords");
        assertThat(script).contains("algorithm", "systemdesign", "takehome", "behavioral",
                "domain", "culture", "casestudy");
    }

    @Test
    void stylesheetFollowsTheOiepDesignAndAccessibilityContract() throws IOException {
        String css = read(STYLE);

        // Shared design tokens (warm paper / ink / correction red), no card SaaS chrome.
        assertThat(css).contains("var(--paper", "var(--ink", "var(--accent", "var(--font-sans");
        assertThat(css).doesNotContain("linear-gradient", "border-radius", "box-shadow");
        // 44px tap target, visible focus, reduced motion, print-safe.
        assertThat(css).contains("min-height: 2.75rem");
        assertThat(css).contains(":focus-visible");
        assertThat(css).contains("@media (prefers-reduced-motion: reduce)");
        assertThat(css).contains("@media print");
        // No horizontal overflow: flexible children and long-word wrapping.
        assertThat(css).contains("min-width: 0");
        assertThat(css).contains("overflow-wrap");
        // No-JS: the bridge-only controls stay hidden, the iframe still works.
        assertThat(css).contains(".no-js .companion-helper__actions");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).as("file must exist: %s", path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
