package com.oiep.companion.user;

import com.oiep.web.ui.PageExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end cover for the optional user helper when the component is enabled and its widget
 * URL is valid: the extension bean exists, the rendered landing page embeds the widget from
 * the configured origin, and the CSP allows exactly that origin.
 *
 * <p>Uses a real embedded Tomcat (not MockMvc) because the contract under test is the
 * rendered JSP output: the generic {@code pageExtensions} loop in {@code index.jsp} must
 * include the fragment, and the fragment must render the configured iframe URL with escaping.
 * A MockMvc test can only assert the view name, not the HTML.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-companion-user-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.companion.enabled=true",
        "oiep.companion.user-helper.widget-url=https://ai.example.org/ui/"
})
class CompanionUserHelperEnabledEndToEndTests {

    private static final String WIDGET_URL = "https://ai.example.org/ui/";
    private static final String WIDGET_ORIGIN = "https://ai.example.org";

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MessageSource messageSource;

    @Test
    void everyHelperMessageExistsInBothBundles() {
        List<String> keys = List.of(
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
        Locale chinese = Locale.forLanguageTag("zh-HK");

        for (String key : keys) {
            String english = messageSource.getMessage(key, null, Locale.ENGLISH);
            String chineseText = messageSource.getMessage(key, null, chinese);
            assertThat(english).as("%s must exist in messages-companion.properties", key)
                    .isNotBlank();
            assertThat(chineseText)
                    .as("%s must exist in messages-companion_zh_HK.properties", key)
                    .isNotBlank();
            assertThat(chineseText)
                    .as("%s must be genuinely translated (host default locale is zh_HK)", key)
                    .isNotEqualTo(english);
        }
    }

    @Test
    void extensionBeanIsRegisteredForTheIndexSlot() {
        CompanionUserHelperExtension extension =
                applicationContext.getBean(CompanionUserHelperExtension.class);

        assertThat(extension.page()).isEqualTo("index");
        assertThat(extension.fragment()).isEqualTo("/WEB-INF/jsp/companion/user-helper.jsp");
        assertThat(extension.getWidgetUrl()).isEqualTo(WIDGET_URL);
        assertThat(extension.getWidgetOrigin()).isEqualTo(WIDGET_ORIGIN);
        assertThat(applicationContext.getBeansOfType(PageExtension.class).values())
                .contains(extension);
    }

    @Test
    void indexRendersTheEmbeddedWidgetFromTheConfiguredOrigin() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.body();

        assertThat(html).contains("data-companion-helper");
        assertThat(html).contains("src=\"" + WIDGET_URL + "\"");
        assertThat(html).contains("loading=\"lazy\"");
        assertThat(html).contains("referrerpolicy=\"no-referrer\"");
        assertThat(html).contains("sandbox=\"allow-scripts allow-forms allow-same-origin\"");
        assertThat(html).contains("allow=\"clipboard-write\"");
        assertThat(html).contains("title=\"Interview-experience helper\"");
        assertThat(html).contains("/css/companion.css");
        assertThat(html).contains("/js/companion.js");
        assertThat(html).contains("data-companion-apply");
        assertThat(html).contains("data-companion-status");
        assertThat(html).contains("data-msg-applied-count");
        // The count placeholder must survive JSP rendering untouched: JS substitutes {0}.
        assertThat(html).contains("{0} field(s)");
        assertThat(html).contains("{0} answer(s)");
        // User copy is rendered through c:out (XML-escaped), so assert on the stable prefix.
        assertThat(html).contains("Use the helper");
        // The rendered page must not leak unresolved JSP/EL expressions.
        assertThat(html).doesNotContain("${", "<spring:message", "<c:out");
    }

    @Test
    void cspAllowsExactlyTheWidgetOriginForFrames() throws Exception {
        HttpResponse<String> response = get("/");

        String csp = response.headers().firstValue("Content-Security-Policy").orElse("");
        assertThat(csp).contains("frame-src 'self' " + WIDGET_ORIGIN);
        assertThat(csp).doesNotContain("frame-src *");
        assertThat(csp).doesNotContain("frame-src 'unsafe-inline'");
    }

    @Test
    void chineseLocaleRendersChineseHelperCopy() throws Exception {
        HttpResponse<String> response = get("/", "Cookie", "OIEP_LANG=zh-HK");

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.body();
        assertThat(html).contains("對著空白格不知道點寫？");
        assertThat(html).contains("把小幫手的答案填入表格");
        assertThat(html).contains("data-msg-applied-count");
        assertThat(html).doesNotContain("${", "<spring:message", "<c:out");
    }

    @Test
    void helperIsOnlyIncludedOnTheIndexPageSlot() throws Exception {
        HttpResponse<String> response = get("/browse");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("data-companion-helper");
        assertThat(response.body()).doesNotContain("/js/companion.js");
    }

    @Test
    void companionStaticAssetsAreServedAndCarryTheBridgeContract() throws Exception {
        HttpResponse<String> script = get("/js/companion.js");
        assertThat(script.statusCode()).isEqualTo(200);
        assertThat(script.headers().firstValue("Content-Type").orElse("")).contains("javascript");
        assertThat(script.body())
                .contains("oiep-ai:request-prefill")
                .contains("oiep-ai:prefill")
                .contains("oiep-ai:prefill-unavailable")
                .contains("event.origin")
                .contains("contentWindow")
                .contains("#debrief-form");

        HttpResponse<String> style = get("/css/companion.css");
        assertThat(style.statusCode()).isEqualTo(200);
        assertThat(style.headers().firstValue("Content-Type").orElse("")).contains("css");
    }

    private HttpResponse<String> get(String path, String... extraHeaders) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET();
        for (int index = 0; index + 1 < extraHeaders.length; index += 2) {
            builder.header(extraHeaders[index], extraHeaders[index + 1]);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
