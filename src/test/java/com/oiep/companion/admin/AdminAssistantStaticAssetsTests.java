package com.oiep.companion.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level guarantees for the operator AI helper assets.
 *
 * <p>MockMvc resolves JSP view names without rendering them, so the page's CSP safety, its
 * external-asset wiring and its i18n completeness are asserted against the files themselves
 * here. The end-to-end verifier boots the application and checks the rendered HTML.</p>
 */
class AdminAssistantStaticAssetsTests {

    private static final Path PAGE = Path.of(
            "src/main/webapp/WEB-INF/jsp/companion/admin-assistant.jsp");
    private static final Path NAV = Path.of(
            "src/main/webapp/WEB-INF/jsp/companion/admin-nav.jsp");
    private static final Path JS = Path.of(
            "src/main/resources/static/js/companion-admin.js");
    private static final Path CSS = Path.of(
            "src/main/resources/static/css/companion-admin.css");
    private static final Path MESSAGES_EN = Path.of(
            "src/main/resources/messages-companion.properties");
    private static final Path MESSAGES_ZH = Path.of(
            "src/main/resources/messages-companion_zh_HK.properties");

    private static final String TOKEN = AdminAssistantTestConstants.ADMIN_TOKEN;

    /* --------------------------------------------------------------- the page */

    @Test
    void pageIsASelfContainedAdminViewUsingTheSharedFragments() throws IOException {
        String jsp = read(PAGE);

        assertThat(jsp)
                .contains("session=\"false\"")
                .contains("../fragments/taglibs.jspf")
                .contains("../fragments/head.jspf")
                .contains("../fragments/admin-topbar.jspf")
                .contains("../fragments/footer.jspf")
                .contains("<c:set var=\"adminCss\" value=\"true\"/>")
                .contains("companion/admin-assistant");
    }

    @Test
    void pageLoadsAssetsExternallySoTheCspHashPolicyIsNotWeakened() throws IOException {
        String jsp = read(PAGE);

        assertThat(jsp)
                .contains("/css/companion-admin.css")
                .contains("/js/companion-admin.js")
                .contains("defer")
                // No inline script/style/event handlers: the app CSP authorises only hashes.
                .doesNotContain("<script>")
                .doesNotContain("<style")
                .doesNotContain("style=\"")
                .doesNotContain("javascript:")
                .doesNotContain("onclick=")
                .doesNotContain("onchange=")
                .doesNotContain("onsubmit=")
                .doesNotContain("onload=")
                .doesNotContain("onerror=");
    }

    @Test
    void pageNeverCarriesTheOperatorTokenOrAServerOnlyReference() throws IOException {
        String page = read(PAGE);
        String nav = read(NAV);
        String js = read(JS);

        for (String source : List.of(page, nav, js)) {
            assertThat(source)
                    .doesNotContain(TOKEN)
                    .doesNotContain("Bearer")
                    .doesNotContain("OIEP_COMPANION_ADMIN_TOKEN")
                    .doesNotContain("OIEP_AI_ADMIN_TOKEN")
                    .doesNotContain("admin-helper.token");
        }
        // The page is handed the same-origin endpoint base, never the companion service origin.
        assertThat(page)
                .contains("${companionApiBase}")
                .doesNotContain("serviceOrigin")
                .doesNotContain("baseUrl");
    }

    @Test
    void pageExposesCsrfTokenOnlyAsADataAttributeForTheFetchClient() throws IOException {
        String jsp = read(PAGE);

        assertThat(jsp)
                .contains("data-api-base")
                .contains("data-csrf")
                .contains("${adminCsrfToken}")
                .contains("${companionApiBase}");
    }

    @Test
    void pageCoversEveryOperatorWorkflowSection() throws IOException {
        String jsp = read(PAGE);

        List<String> sectionKeys = List.of(
                "companion.admin.health.title",
                "companion.admin.health.refresh",
                "companion.admin.chat.title",
                "companion.admin.chat.send",
                "companion.admin.chat.clear",
                "companion.admin.query.title",
                "companion.admin.query.run",
                "companion.admin.summarise.title",
                "companion.admin.summarise.run",
                "companion.admin.sync.title",
                "companion.admin.sync.incremental",
                "companion.admin.sync.full",
                "companion.admin.sync.auditLoad",
                "companion.admin.readOnly");

        assertThat(jsp).contains(sectionKeys.toArray(new String[0]));
    }

    /* ----------------------------------------------------------- the nav link */

    @Test
    void navFragmentIsAStandaloneJspDeclaringItsOwnTaglibs() throws IOException {
        String nav = read(NAV);

        assertThat(nav)
                .contains("session=\"false\"")
                .contains("../fragments/taglibs.jspf")
                .contains("<c:set var=\"ctx\"")
                .contains("/admin/assistant")
                .contains("companion.admin.nav")
                .doesNotContain("<html")
                .doesNotContain("<body")
                .doesNotContain("<script")
                .doesNotContain("style=\"");
    }

    /* --------------------------------------------------------------- the code */

    @Test
    void scriptUsesTextContentOnlyAndNeverInjectsHtml() throws IOException {
        String js = read(JS);

        assertThat(js)
                .contains("textContent")
                .contains("fetch(")
                .contains("X-OIEP-CSRF")
                .contains("credentials")
                .doesNotContain("innerHTML")
                .doesNotContain("outerHTML")
                .doesNotContain("insertAdjacentHTML")
                .doesNotContain("document.write")
                .doesNotContain("eval(")
                .doesNotContain("Function(");
        // Same-origin only: the script must never build an absolute or protocol-relative URL.
        assertThat(js)
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain("//127.")
                .doesNotContain("//localhost");
    }

    @Test
    void scriptWiresEveryProxyEndpoint() throws IOException {
        String js = read(JS);

        assertThat(js).contains(
                "/health",
                "/corpus/sync",
                "/chat",
                "/query",
                "/summarise",
                "/audit");
    }

    @Test
    void stylesheetStaysLocalAndMatchesTheAdminDesignTokens() throws IOException {
        String css = read(CSS);

        assertThat(css)
                .contains("companion-admin")
                // The admin surface uses the tokens defined by site.css.
                .contains("var(--ink)")
                .contains("var(--paper)")
                .doesNotContain("@import")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }

    /* --------------------------------------------------------------- i18n */

    @Test
    void everyMessageCodeUsedByTheComponentExistsInBothLanguages() throws IOException {
        Set<String> usedCodes = new LinkedHashSet<>();
        usedCodes.addAll(messageCodes(read(PAGE)));
        usedCodes.addAll(messageCodes(read(NAV)));

        assertThat(usedCodes).isNotEmpty();
        // Guards against a missing key silently rendering ???code??? in one language.
        assertThat(usedCodes).contains("companion.admin.nav", "companion.admin.title",
                "companion.admin.health.title", "companion.admin.audit.time");

        Set<String> englishKeys = keys(MESSAGES_EN);
        Set<String> chineseKeys = keys(MESSAGES_ZH);
        for (String code : usedCodes) {
            assertThat(englishKeys).as("English bundle key %s", code).contains(code);
            assertThat(chineseKeys).as("zh-HK bundle key %s", code).contains(code);
        }
    }

    /* -------------------------------------------------------------- helpers */

    private static String read(Path path) throws IOException {
        assertThat(path).as("Required companion admin asset %s", path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Set<String> messageCodes(String jsp) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("code=\"(companion\\.[^\"]+)\"|code='(companion\\.[^']+)'")
                .matcher(jsp);
        while (matcher.find()) {
            codes.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        return codes;
    }

    private static Set<String> keys(Path properties) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(properties, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator > 0) {
                keys.add(trimmed.substring(0, separator).trim());
            }
        }
        return keys;
    }
}
