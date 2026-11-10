package com.oiep.companion.admin;

import com.oiep.service.AdminAuth;
import com.oiep.service.AdminLoginPageRateLimiter;
import com.oiep.service.AdminLoginRateLimiter;
import com.oiep.service.HelpfulVoteRateLimiter;
import com.oiep.service.SubmissionRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-level tests: the proxy contract when the companion service is unreachable, plus the
 * actually rendered JSP pages (this context boots a real servlet container, which compiles and
 * renders JSPs; MockMvc-only contexts resolve view names without rendering them).
 *
 * <p>Rendered-page assertions pin the user-visible security guarantees: the operator token is
 * absent from the HTML, the page loads only external same-origin assets (CSP hash policy stays
 * intact), the navigation extension is rendered on admin pages, and both languages render from
 * the message bundles.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-companion-admin-down-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
                "oiep.demo-data=false",
                "oiep.admin.username=" + AdminAssistantTestConstants.ADMIN_USER,
                "oiep.admin.password=" + AdminAssistantTestConstants.ADMIN_PASSWORD,
                "oiep.companion.enabled=true",
                "oiep.companion.base-url=http://127.0.0.1:1",
                "oiep.companion.user-helper.enabled=false",
                "oiep.companion.read-api.enabled=false",
                "oiep.companion.admin-helper.enabled=true",
                "oiep.companion.admin-helper.token=" + AdminAssistantTestConstants.ADMIN_TOKEN,
                "oiep.companion.http.connect-timeout-ms=500",
                "oiep.companion.http.request-timeout-ms=500"
        })
@AutoConfigureMockMvc
class AdminAssistantServerTests {

    private static final String API = "/admin/assistant/api";
    private static final String CSRF_HEADER = AdminAssistantController.CSRF_HEADER;
    private static final Pattern CSRF_INPUT =
            Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubmissionRateLimiter rateLimiter;

    @Autowired
    private AdminLoginRateLimiter adminLoginRateLimiter;

    @Autowired
    private AdminLoginPageRateLimiter adminLoginPageRateLimiter;

    @Autowired
    private HelpfulVoteRateLimiter helpfulVoteRateLimiter;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM reports");
        rateLimiter.reset();
        adminLoginRateLimiter.reset();
        adminLoginPageRateLimiter.reset();
        helpfulVoteRateLimiter.reset();
    }

    /* ------------------------------------- companion unreachable (MockMvc path) */

    @Test
    void pageStillRendersWhileTheCompanionIsDown() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/admin/assistant").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void transportFailureBecomesAFriendly502() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get(API + "/health").session(session))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("companion_unavailable"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(AdminAssistantTestConstants.ADMIN_TOKEN);
        assertThat(body).doesNotContain("127.0.0.1");
        assertThat(body).doesNotContain("Connection refused");
    }

    @Test
    void transportFailureOnPostAlsoBecomesA502() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How many offers?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("companion_unavailable"));
    }

    /* ------------------------------- rendered JSP (real servlet container path) */

    @Test
    void renderedPageLoadsOnlyExternalAssetsAndNeverCarriesTheToken() throws Exception {
        HttpClient browser = newBrowser();
        login(browser);

        HttpResponse<String> page = http(browser, "GET", "/admin/assistant");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Cache-Control").orElse(""))
                .contains("no-store");
        assertThat(page.headers().firstValue("X-Robots-Tag").orElse(""))
                .contains("noindex");

        String html = page.body();
        assertThat(html)
                .contains("<html lang=\"en\"")
                .contains("id=\"companion-admin\"")
                .contains("data-api-base=\"/admin/assistant/api\"")
                .contains("data-csrf=\"")
                .contains("/js/companion-admin.js")
                .contains("/css/companion-admin.css")
                .contains("AI helper (operator only)")
                .contains("Read-only")
                .contains("Ask the corpus")
                .contains("aria-live=\"polite\"")
                .contains("role=\"status\"")
                // Every message code resolved (both languages are covered by the bundles).
                .doesNotContain("???")
                .doesNotContain("${")
                .doesNotContain(AdminAssistantTestConstants.ADMIN_TOKEN)
                .doesNotContain("Bearer ")
                // Absolute companion origin must never be rendered into the page.
                .doesNotContain("127.0.0.1:1");
    }

    @Test
    void renderedAdminPagesShowTheNavigationLink() throws Exception {
        HttpClient browser = newBrowser();
        login(browser);

        HttpResponse<String> dashboard = http(browser, "GET", "/admin");
        assertThat(dashboard.statusCode()).isEqualTo(200);
        assertThat(dashboard.body())
                .contains("href=\"/admin/assistant\"")
                .contains("AI helper");

        HttpResponse<String> assistant = http(browser, "GET", "/admin/assistant");
        assertThat(assistant.body())
                .contains("href=\"/admin/assistant\" aria-current=\"page\"");
    }

    @Test
    void renderedPageIsLocalisedInChineseWhenTheCookieIsSet() throws Exception {
        HttpClient browser = newBrowser();
        login(browser);

        HttpResponse<String> language = http(browser, "GET", "/language/zh-HK?returnTo=/admin/assistant");
        assertThat(language.statusCode()).isBetween(300, 399);

        HttpResponse<String> page = http(browser, "GET", "/admin/assistant");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body())
                .contains("<html lang=\"zh-HK\"")
                .contains("AI 小幫手（只限營運者）")
                .contains("唯讀");
    }

    /* --------------------------------------------------------------- helpers */

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private HttpClient newBrowser() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpResponse<String> http(HttpClient browser, String method, String pathAndQuery)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + pathAndQuery))
                .timeout(Duration.ofSeconds(30));
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }
        return browser.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Logs in through the real login form: the session cookie carries the admin session. */
    private void login(HttpClient browser) throws Exception {
        HttpResponse<String> loginPage = http(browser, "GET", "/admin/login");
        assertThat(loginPage.statusCode()).isEqualTo(200);
        Matcher matcher = CSRF_INPUT.matcher(loginPage.body());
        assertThat(matcher.find()).as("login page carries a CSRF token").isTrue();
        String csrf = matcher.group(1);

        String form = "username=" + encode(AdminAssistantTestConstants.ADMIN_USER)
                + "&password=" + encode(AdminAssistantTestConstants.ADMIN_PASSWORD)
                + "&_csrf=" + encode(csrf);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/admin/login"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = browser.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isBetween(300, 399);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private MockHttpSession login() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession loginSession = (MockHttpSession) loginPage.getRequest().getSession(false);
        assertThat(loginSession).isNotNull();

        MvcResult result = mockMvc.perform(post("/admin/login").session(loginSession)
                        .param("_csrf", csrf(loginSession))
                        .param("username", AdminAssistantTestConstants.ADMIN_USER)
                        .param("password", AdminAssistantTestConstants.ADMIN_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        return authenticated;
    }

    private static String csrf(MockHttpSession session) {
        Object token = session.getAttribute(AdminAuth.CSRF_SESSION_KEY);
        return token instanceof String value ? value : "";
    }
}
