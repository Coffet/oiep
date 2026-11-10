package com.oiep.companion.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oiep.companion.StubCompanionServer;
import com.oiep.service.AdminAuth;
import com.oiep.service.AdminLoginPageRateLimiter;
import com.oiep.service.AdminLoginRateLimiter;
import com.oiep.service.HelpfulVoteRateLimiter;
import com.oiep.service.SubmissionRateLimiter;
import com.oiep.web.ui.PageExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for the operator-only AI helper page and its same-origin proxy.
 *
 * <p>The companion service is replaced by the in-process {@link StubCompanionServer}, so these
 * tests exercise the real HTTP client, the real admin session/CSRF code and the real controller
 * without a network or a model. They pin the security contract: admin-only access, the companion
 * bearer token never reaching a response, allowlisted/size-bounded request bodies, no-store
 * responses and the status mapping for companion failures.</p>
 *
 * <p>Note that MockMvc resolves JSP view names without rendering them; {@link
 * AdminAssistantStaticAssetsTests} covers the JSP/JS/CSS source-level guarantees instead, and the
 * end-to-end verifier boots the app and curls the rendered page.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-companion-admin-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=" + AdminAssistantTestConstants.ADMIN_USER,
        "oiep.admin.password=" + AdminAssistantTestConstants.ADMIN_PASSWORD,
        "oiep.companion.enabled=true",
        "oiep.companion.user-helper.enabled=false",
        "oiep.companion.read-api.enabled=false",
        "oiep.companion.admin-helper.enabled=true",
        "oiep.companion.admin-helper.token=" + AdminAssistantTestConstants.ADMIN_TOKEN,
        "oiep.companion.http.connect-timeout-ms=1000",
        "oiep.companion.http.request-timeout-ms=2000"
})
@AutoConfigureMockMvc
class AdminAssistantTests {

    private static final String API = "/admin/assistant/api";
    private static final String CSRF_HEADER = AdminAssistantController.CSRF_HEADER;

    private static final StubCompanionServer COMPANION = new StubCompanionServer();

    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void companionBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("oiep.companion.base-url", COMPANION::baseUrl);
    }

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
        COMPANION.reset();
    }

    /* ---------------------------------------------------------------- access */

    @Test
    void anonymousPageRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/assistant"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void anonymousProxyRequestRedirectsToLoginWithoutCallingTheCompanion() throws Exception {
        mockMvc.perform(get(API + "/health"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
        assertThat(COMPANION.requestCount()).isZero();
    }

    @Test
    void authenticatedPageRendersModelHeadersAndNoToken() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get("/admin/assistant").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("companion/admin-assistant"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Robots-Tag", containsString("noindex")))
                .andExpect(model().attribute("adminAuthenticated", true))
                .andExpect(model().attribute("activeNav", "admin"))
                .andExpect(model().attribute("currentUrl", "/admin/assistant"))
                .andExpect(model().attribute("companionApiBase", API))
                .andExpect(model().attributeExists("adminCsrfToken", "pageTitle", "pageDescription"))
                .andReturn();

        assertThat(csrf(session)).isNotBlank();

        // Nothing the browser receives — model or response body — may carry the operator token.
        Map<String, Object> model = result.getModelAndView().getModel();
        for (Object value : model.values()) {
            assertThat(String.valueOf(value))
                    .doesNotContain(AdminAssistantTestConstants.ADMIN_TOKEN);
        }
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(AdminAssistantTestConstants.ADMIN_TOKEN);
    }

    @Test
    void navExtensionIsRegisteredAndExposedToAdminPages() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andReturn();

        Object extensions = result.getModelAndView().getModel().get("pageExtensions");
        assertThat(extensions).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<PageExtension> pageExtensions = (List<PageExtension>) extensions;
        assertThat(pageExtensions)
                .anySatisfy(extension -> {
                    assertThat(extension.page()).isEqualTo("admin-nav");
                    assertThat(extension.fragment())
                            .isEqualTo("/WEB-INF/jsp/companion/admin-nav.jsp");
                });
    }

    /* ------------------------------------------------------- GET proxy routes */

    @Test
    void healthProxyForwardsBearerTokenAndPassesTheResponseThrough() throws Exception {
        MockHttpSession session = login();
        String companionBody = "{\"service\":\"oiep-backendai\",\"mode\":\"admin\","
                + "\"corpus\":{\"experiences\":137},\"model\":{\"backend\":\"structured-extractive\",\"degraded\":false}}";
        COMPANION.enqueue(200, companionBody);

        MvcResult result = mockMvc.perform(get(API + "/health").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Robots-Tag", containsString("noindex")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).isEqualTo(companionBody);
        assertThat(body).doesNotContain(AdminAssistantTestConstants.ADMIN_TOKEN);

        StubCompanionServer.RecordedRequest request = COMPANION.lastRequest();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/v1/admin/health");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization"))
                .isEqualTo("Bearer " + AdminAssistantTestConstants.ADMIN_TOKEN);
        assertThat(request.header("Accept")).isEqualTo("application/json");
        // The browser's session cookie must never be forwarded to the companion service.
        assertThat(request.header("Cookie")).isNull();
    }

    @Test
    void corpusStatsAndSyncStateProxyToTheirFixedRoutes() throws Exception {
        MockHttpSession session = login();

        COMPANION.enqueue(200, "{\"stats\":{\"total\":3}}");
        mockMvc.perform(get(API + "/corpus/stats").session(session))
                .andExpect(status().isOk());
        assertThat(COMPANION.lastRequest().path()).isEqualTo("/v1/admin/corpus/stats");
        assertThat(COMPANION.lastRequest().method()).isEqualTo("GET");

        COMPANION.enqueue(200, "{\"configured\":true,\"corpus_total\":3}");
        mockMvc.perform(get(API + "/corpus/sync").session(session))
                .andExpect(status().isOk());
        assertThat(COMPANION.lastRequest().path()).isEqualTo("/v1/admin/corpus/sync");
        assertThat(COMPANION.lastRequest().method()).isEqualTo("GET");
    }

    /* --------------------------------------------------------------- CSRF */

    @Test
    void postWithoutCsrfHeaderIsRejectedAndNeverReachesTheCompanion() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post(API + "/chat").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How many offers?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.error.code").value("csrf_invalid"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    @Test
    void postWithWrongCsrfHeaderIsRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, "not-the-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How many offers?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("csrf_invalid"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    @Test
    void csrfTokenInQueryStringIsRejectedEvenWithAValidHeader() throws Exception {
        MockHttpSession session = login();
        String csrf = csrf(session);

        mockMvc.perform(post(API + "/chat?_csrf=" + csrf).session(session)
                        .header(CSRF_HEADER, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How many offers?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("csrf_invalid"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    /* --------------------------------------------------------- POST proxying */

    @Test
    void chatPostWithValidCsrfIsProxiedWithOnlyAllowlistedFields() throws Exception {
        MockHttpSession session = login();
        String csrf = csrf(session);
        String companionBody = "{\"conversation_id\":\"ac_1\",\"answer\":\"4 experiences match\"}";
        COMPANION.enqueue(200, companionBody);

        String payload = "{\"message\":\"How many offers?\",\"conversation_id\":null,"
                + "\"evil\":\"DROP TABLE reports\",\"headers\":{\"Authorization\":\"Bearer x\"}}";

        MvcResult result = mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(companionBody);

        StubCompanionServer.RecordedRequest request = COMPANION.lastRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/admin/chat");
        assertThat(request.header("Authorization"))
                .isEqualTo("Bearer " + AdminAssistantTestConstants.ADMIN_TOKEN);
        assertThat(request.header("Content-Type")).isEqualTo("application/json");

        JsonNode forwarded = JSON.readTree(request.body());
        assertThat(forwarded.has("message")).isTrue();
        assertThat(forwarded.get("message").asText()).isEqualTo("How many offers?");
        assertThat(forwarded.has("conversation_id")).isTrue();
        assertThat(forwarded.has("evil")).isFalse();
        assertThat(forwarded.has("headers")).isFalse();
        assertThat(request.body()).doesNotContain("DROP TABLE");
        assertThat(request.body()).doesNotContain("Bearer x");
    }

    @Test
    void queryPostKeepsTheDslAndDropsUnknownFields() throws Exception {
        MockHttpSession session = login();
        COMPANION.enqueue(200, "{\"rows\":[],\"row_count\":0,\"truncated\":false}");

        String payload = "{\"where\":[{\"field\":\"outcome\",\"op\":\"eq\",\"value\":\"OFFER\"}],"
                + "\"group_by\":[\"company\"],\"limit\":20,\"sql\":\"SELECT * FROM reports\"}";

        mockMvc.perform(post(API + "/query").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        StubCompanionServer.RecordedRequest request = COMPANION.lastRequest();
        assertThat(request.path()).isEqualTo("/v1/admin/query");
        assertThat(request.method()).isEqualTo("POST");
        JsonNode forwarded = JSON.readTree(request.body());
        assertThat(forwarded.has("where")).isTrue();
        assertThat(forwarded.has("group_by")).isTrue();
        assertThat(forwarded.has("limit")).isTrue();
        assertThat(forwarded.has("sql")).isFalse();
        assertThat(request.body()).doesNotContain("SELECT");
    }

    @Test
    void summarisePostValidatesTheFocusAllowlist() throws Exception {
        MockHttpSession session = login();
        COMPANION.enqueue(200, "{\"summary\":{\"headline\":\"ok\"}}");

        mockMvc.perform(post(API + "/summarise").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filters\":{},\"focus\":\"outcomes\",\"limit\":10}"))
                .andExpect(status().isOk());

        StubCompanionServer.RecordedRequest valid = COMPANION.lastRequest();
        assertThat(valid.path()).isEqualTo("/v1/admin/summarise");
        JsonNode forwarded = JSON.readTree(valid.body());
        assertThat(forwarded.has("filters")).isTrue();
        assertThat(forwarded.get("focus").asText()).isEqualTo("outcomes");

        mockMvc.perform(post(API + "/summarise").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"focus\":\"everything\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));
        assertThat(COMPANION.requestCount()).isEqualTo(1);
    }

    @Test
    void syncPostAcceptsTheFullFlagAndAllowsAnEmptyBody() throws Exception {
        MockHttpSession session = login();
        String csrf = csrf(session);

        COMPANION.enqueue(200, "{\"status\":\"ok\",\"requested_full\":true}");
        mockMvc.perform(post(API + "/corpus/sync").session(session)
                        .header(CSRF_HEADER, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full\":true,\"admin\":\"x\"}"))
                .andExpect(status().isOk());

        StubCompanionServer.RecordedRequest full = COMPANION.lastRequest();
        assertThat(full.path()).isEqualTo("/v1/admin/corpus/sync");
        assertThat(full.method()).isEqualTo("POST");
        JsonNode forwarded = JSON.readTree(full.body());
        assertThat(forwarded.get("full").asBoolean()).isTrue();
        assertThat(forwarded.has("admin")).isFalse();

        COMPANION.enqueue(200, "{\"status\":\"ok\",\"requested_full\":false}");
        mockMvc.perform(post(API + "/corpus/sync").session(session)
                        .header(CSRF_HEADER, csrf)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        assertThat(COMPANION.lastRequest().body()).isEqualTo("{}");
    }

    /* ------------------------------------------------------ local validation */

    @Test
    void invalidJsonBodyIsRejectedWithoutCallingTheCompanion() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    @Test
    void nonObjectJsonBodyIsRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post(API + "/query").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2,3]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    @Test
    void invalidChatMessageIsRejectedLocally() throws Exception {
        MockHttpSession session = login();
        String csrf = csrf(session);

        mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        String tooLong = "x".repeat(AdminAssistantController.MAX_CHAT_MESSAGE_CHARS + 1);
        mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    @Test
    void oversizedBodyIsRejectedBeforeAnyCompanionCall() throws Exception {
        MockHttpSession session = login();
        String tooLarge = "{\"message\":\"" + "a".repeat(AdminAssistantController.MAX_BODY_BYTES + 8) + "\"}";

        mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooLarge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("body_too_large"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    @Test
    void nonJsonContentTypeIsRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post(API + "/chat").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("unsupported_media_type"));

        assertThat(COMPANION.requestCount()).isZero();
    }

    /* ------------------------------------------------------ failure mapping */

    @Test
    void companionServerErrorBecomesAFriendly502WithoutEchoingTheBody() throws Exception {
        MockHttpSession session = login();
        COMPANION.enqueue(500, "{\"detail\":\"internal token check failed for super-secret-value\"}");

        MvcResult result = mockMvc.perform(get(API + "/health").session(session))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.error.code").value("companion_unavailable"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("super-secret-value");
        assertThat(body).doesNotContain("internal token check");
        assertThat(body).doesNotContain(AdminAssistantTestConstants.ADMIN_TOKEN);
    }

    @Test
    void companionAuthenticationRejectionBecomesAGeneric502() throws Exception {
        MockHttpSession session = login();
        COMPANION.enqueue(401, "{\"detail\":\"OIEP_AI_ADMIN_TOKEN is invalid or missing\"}");

        MvcResult result = mockMvc.perform(get(API + "/health").session(session))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("companion_auth_failed"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("OIEP_AI_ADMIN_TOKEN");
        assertThat(body).doesNotContain(AdminAssistantTestConstants.ADMIN_TOKEN);
    }

    @Test
    void companionForbiddenAlsoBecomesAGeneric502() throws Exception {
        MockHttpSession session = login();
        COMPANION.enqueue(403, "{\"detail\":\"admin allowlist rejected this address\"}");

        MvcResult result = mockMvc.perform(get(API + "/health").session(session))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("companion_auth_failed"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("allowlist");
    }

    @Test
    void companionValidationErrorIsPassedThroughForOperatorFeedback() throws Exception {
        MockHttpSession session = login();
        String companionBody = "{\"error\":{\"code\":\"validation_error\","
                + "\"message\":\"unknown field sql\"}}";
        COMPANION.enqueue(400, companionBody);

        MvcResult result = mockMvc.perform(post(API + "/query").session(session)
                        .header(CSRF_HEADER, csrf(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(companionBody);
    }

    /* ------------------------------------------------------------- audit log */

    @Test
    void auditLimitIsClampedAndTheForwardedPathIsServerConstructed() throws Exception {
        MockHttpSession session = login();

        COMPANION.enqueue(200, "{\"entries\":[],\"count\":0}");
        mockMvc.perform(get(API + "/audit?limit=9999").session(session))
                .andExpect(status().isOk());
        assertThat(COMPANION.lastRequest().path()).isEqualTo("/v1/admin/audit");
        assertThat(COMPANION.lastRequest().query()).isEqualTo("limit=200");

        COMPANION.enqueue(200, "{\"entries\":[],\"count\":0}");
        mockMvc.perform(get(API + "/audit?limit=-5").session(session))
                .andExpect(status().isOk());
        assertThat(COMPANION.lastRequest().query()).isEqualTo("limit=1");

        COMPANION.enqueue(200, "{\"entries\":[],\"count\":0}");
        mockMvc.perform(get(API + "/audit").session(session))
                .andExpect(status().isOk());
        assertThat(COMPANION.lastRequest().query()).isEqualTo("limit=50");

        COMPANION.enqueue(200, "{\"entries\":[],\"count\":0}");
        mockMvc.perform(get(API + "/audit?limit=not-a-number").session(session))
                .andExpect(status().isOk());
        assertThat(COMPANION.lastRequest().query()).isEqualTo("limit=50");
    }

    /* --------------------------------------------------------------- helpers */

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
        assertThat(csrf(authenticated)).isNotBlank();
        return authenticated;
    }

    private static String csrf(MockHttpSession session) {
        Object token = session.getAttribute(AdminAuth.CSRF_SESSION_KEY);
        return token instanceof String value ? value : "";
    }

    /** Keeps the unused-matcher import honest: the header contract must never be a wildcard. */
    @Test
    void proxyNeverEnablesCorsOrAllowsAForeignOrigin() throws Exception {
        MockHttpSession session = login();
        COMPANION.enqueue(200, "{\"ok\":true}");

        mockMvc.perform(get(API + "/health").session(session)
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().string("X-Robots-Tag", not(containsString("index, follow"))));
    }
}
