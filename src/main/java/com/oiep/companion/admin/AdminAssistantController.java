package com.oiep.companion.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oiep.companion.CompanionConditions;
import com.oiep.companion.client.CompanionClient;
import com.oiep.companion.client.CompanionResponse;
import com.oiep.companion.client.CompanionUnavailableException;
import com.oiep.service.AdminAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Operator-only AI helper page ({@code /admin/assistant}) and its same-origin proxy.
 *
 * <p>The browser never talks to the companion service and never sees its operator token. Every
 * request is authenticated by the existing admin interceptor, POSTs additionally require the
 * per-session {@code X-OIEP-CSRF} token, and the controller forwards only a fixed allowlist of
 * routes and JSON fields through {@link CompanionClient}. Unknown paths, unknown fields and
 * oversized bodies are rejected locally, so the proxy can never be turned into an open relay
 * (SSRF) or a way to submit arbitrary operator payloads.</p>
 *
 * <p>Response policy: companion 2xx/4xx are passed through (4xx only when the body is JSON),
 * transport failures/5xx/redirects become a friendly {@code 502 companion_unavailable}, and a
 * companion {@code 401/403} becomes a generic {@code 502 companion_auth_failed}: the service
 * body is never echoed for those, because it could describe server configuration. The token,
 * request bodies and companion bodies are never logged.</p>
 *
 * <p>This bean only exists while the {@link CompanionConditions.AdminHelper} condition holds
 * (component enabled, admin helper enabled, valid base URL and a usable token), so a disabled
 * or misconfigured install has no page and no routes.</p>
 */
@Controller
@Conditional(CompanionConditions.AdminHelper.class)
public class AdminAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AdminAssistantController.class);

    /** Page route (the admin interceptor already protects every {@code /admin/**} route). */
    static final String PAGE_PATH = "/admin/assistant";

    /** Same-origin base the page JavaScript calls; never the companion origin. */
    static final String API_BASE = PAGE_PATH + "/api";

    static final String CSRF_HEADER = "X-OIEP-CSRF";

    /** Maximum accepted request body size, in bytes (inclusive). */
    static final int MAX_BODY_BYTES = 64 * 1024;

    /** The companion caps a chat message at 4000 characters; enforce it before spending a call. */
    static final int MAX_CHAT_MESSAGE_CHARS = 4000;

    static final int MIN_AUDIT_LIMIT = 1;
    static final int MAX_AUDIT_LIMIT = 200;
    static final int DEFAULT_AUDIT_LIMIT = 50;

    private static final String CACHE_CONTROL_ADMIN = "no-store, no-cache, must-revalidate";
    private static final String ROBOTS_NOINDEX = "noindex, nofollow";

    /* Fixed companion routes: the only paths this proxy can ever call. */
    private static final String COMPANION_HEALTH = "/v1/admin/health";
    private static final String COMPANION_CORPUS_STATS = "/v1/admin/corpus/stats";
    private static final String COMPANION_CORPUS_SYNC = "/v1/admin/corpus/sync";
    private static final String COMPANION_CHAT = "/v1/admin/chat";
    private static final String COMPANION_QUERY = "/v1/admin/query";
    private static final String COMPANION_SUMMARISE = "/v1/admin/summarise";
    private static final String COMPANION_AUDIT = "/v1/admin/audit";

    /* Top-level JSON fields accepted per route; everything else is dropped before forwarding. */
    private static final Set<String> CHAT_FIELDS = Set.of("message", "conversation_id");
    private static final Set<String> QUERY_FIELDS = Set.of("where", "group_by", "aggregate", "order_by", "limit");
    private static final Set<String> SUMMARISE_FIELDS = Set.of("filters", "focus", "limit");
    private static final Set<String> SYNC_FIELDS = Set.of("full");

    /** The summary focus values documented by the companion service and offered by the page. */
    private static final Set<String> SUMMARISE_FOCI =
            Set.of("overview", "outcomes", "rounds", "locations", "difficulty", "recent");

    /**
     * CSRF tokens belong in the explicit header (or a request body), never in a URL: URLs end up
     * in browser history, proxy logs and {@code Referer} headers. Any POST that carries
     * {@code _csrf} in its query string is rejected instead of accepted.
     */
    private static final Pattern CSRF_IN_QUERY_STRING =
            Pattern.compile("(?:^|&)_csrf=", Pattern.CASE_INSENSITIVE);

    private final CompanionClient companionClient;
    private final AdminAuth adminAuth;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public AdminAssistantController(CompanionClient companionClient,
                                    AdminAuth adminAuth,
                                    MessageSource messageSource,
                                    ObjectMapper objectMapper) {
        this.companionClient = companionClient;
        this.adminAuth = adminAuth;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    /* ------------------------------------------------------------------ page */

    @GetMapping(PAGE_PATH)
    public String page(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        model.addAttribute("adminAuthenticated", true);
        model.addAttribute("adminCsrfToken", adminAuth.csrfToken(session));
        model.addAttribute("currentUrl", PAGE_PATH);
        model.addAttribute("activeNav", "admin");
        model.addAttribute("pageTitle", message("companion.admin.metaTitle", "AI helper — OIEP admin"));
        model.addAttribute("pageDescription", message("companion.admin.metaDescription", ""));
        // The browser-facing endpoint base only; the companion origin and token stay server-side.
        model.addAttribute("companionApiBase", request.getContextPath() + API_BASE);
        return "companion/admin-assistant";
    }

    /* ------------------------------------------------------------- GET proxy */

    @GetMapping(API_BASE + "/health")
    public ResponseEntity<String> health() {
        return getFromCompanion(COMPANION_HEALTH);
    }

    @GetMapping(API_BASE + "/corpus/stats")
    public ResponseEntity<String> corpusStats() {
        return getFromCompanion(COMPANION_CORPUS_STATS);
    }

    @GetMapping(API_BASE + "/corpus/sync")
    public ResponseEntity<String> corpusSyncState() {
        return getFromCompanion(COMPANION_CORPUS_SYNC);
    }

    /**
     * Recent audit entries. The {@code limit} is parsed, defaulted and clamped here so the
     * forwarded query string is always server-constructed (1..200) and can never carry
     * attacker-controlled path/query fragments.
     */
    @GetMapping(API_BASE + "/audit")
    public ResponseEntity<String> audit(@RequestParam(name = "limit", required = false) String limit) {
        return getFromCompanion(COMPANION_AUDIT + "?limit=" + clampAuditLimit(limit));
    }

    /* ------------------------------------------------------------ POST proxy */

    @PostMapping(API_BASE + "/chat")
    public ResponseEntity<String> chat(HttpServletRequest request) {
        return postToCompanion(request, COMPANION_CHAT, CHAT_FIELDS,
                AdminAssistantController::isValidChatBody);
    }

    @PostMapping(API_BASE + "/query")
    public ResponseEntity<String> query(HttpServletRequest request) {
        return postToCompanion(request, COMPANION_QUERY, QUERY_FIELDS,
                AdminAssistantController::isValidQueryBody);
    }

    @PostMapping(API_BASE + "/summarise")
    public ResponseEntity<String> summarise(HttpServletRequest request) {
        return postToCompanion(request, COMPANION_SUMMARISE, SUMMARISE_FIELDS,
                AdminAssistantController::isValidSummariseBody);
    }

    @PostMapping(API_BASE + "/corpus/sync")
    public ResponseEntity<String> triggerCorpusSync(HttpServletRequest request) {
        return postToCompanion(request, COMPANION_CORPUS_SYNC, SYNC_FIELDS,
                AdminAssistantController::isValidSyncBody);
    }

    /* ---------------------------------------------------------------- helpers */

    private ResponseEntity<String> getFromCompanion(String companionPathAndQuery) {
        try {
            return toHttpResponse(companionClient.get(companionPathAndQuery));
        } catch (CompanionUnavailableException ex) {
            return unavailable(ex, companionPathAndQuery);
        }
    }

    /**
     * Validates a POST locally (CSRF, content type, size, JSON shape, allowlisted fields and the
     * route's required fields) and only then calls the companion service.
     */
    private ResponseEntity<String> postToCompanion(HttpServletRequest request,
                                                   String companionPath,
                                                   Set<String> allowedFields,
                                                   Predicate<ObjectNode> validator) {
        if (!csrfValid(request)) {
            return error(HttpStatus.FORBIDDEN, "csrf_invalid",
                    message("companion.admin.error.csrf",
                            "Your session could not be verified. Reload the page and try again."));
        }
        if (!isJsonContentType(request)) {
            return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
                    message("companion.admin.error.request",
                            "The request was rejected. Check the values and try again."));
        }

        byte[] body;
        try {
            body = readBoundedBody(request);
        } catch (IOException ex) {
            log.warn("AI companion admin proxy could not read a request body route={}", companionPath);
            return error(HttpStatus.BAD_REQUEST, "invalid_request",
                    message("companion.admin.error.request",
                            "The request was rejected. Check the values and try again."));
        }
        if (body == null) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "body_too_large",
                    message("companion.admin.error.request",
                            "The request was rejected. Check the values and try again."));
        }

        ObjectNode filtered = filterJsonObject(body, allowedFields);
        if (filtered == null || !validator.test(filtered)) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request",
                    message("companion.admin.error.request",
                            "The request was rejected. Check the values and try again."));
        }

        try {
            return toHttpResponse(companionClient.postJson(companionPath, filtered.toString()));
        } catch (CompanionUnavailableException ex) {
            return unavailable(ex, companionPath);
        }
    }

    /**
     * Maps a companion response onto the browser contract. Only JSON bodies from 2xx/4xx are
     * passed through; authentication failures and server errors are replaced with a generic,
     * non-revealing envelope so companion configuration details cannot leak to the operator UI.
     */
    private ResponseEntity<String> toHttpResponse(CompanionResponse response) {
        int status = response.status();
        String body = response.body() == null ? "" : response.body();

        if (status >= 200 && status < 300) {
            if (!isJson(body)) {
                log.warn("AI companion admin proxy returned a non-JSON success body status={}", status);
                return unavailableEnvelope("companion_unavailable");
            }
            return jsonResponse(status, body);
        }
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            // Never echo the companion's body: it may describe which credential check failed.
            log.warn("AI companion admin proxy authentication rejected by the companion service status={}", status);
            return error(HttpStatus.BAD_GATEWAY, "companion_auth_failed",
                    message("companion.admin.error", "The helper service could not be reached."));
        }
        if (status >= 400 && status < 500) {
            if (!isJson(body)) {
                log.warn("AI companion admin proxy returned a non-JSON client error status={}", status);
                return unavailableEnvelope("companion_unavailable");
            }
            // Validation errors are useful operator feedback and contain no configuration detail.
            return jsonResponse(status, body);
        }
        // 3xx (redirects are never followed) and 5xx are service failures from our point of view.
        log.warn("AI companion admin proxy request failed status={}", status);
        return unavailableEnvelope("companion_unavailable");
    }

    private ResponseEntity<String> unavailable(CompanionUnavailableException ex, String companionPathAndQuery) {
        // The exception message is contractually token/body free; the route is server-constructed.
        log.warn("AI companion admin proxy unavailable route={} reason={}",
                companionPathAndQuery, ex.getMessage());
        return unavailableEnvelope("companion_unavailable");
    }

    private ResponseEntity<String> unavailableEnvelope(String code) {
        return error(HttpStatus.BAD_GATEWAY, code,
                message("companion.admin.error", "The helper service could not be reached."));
    }

    private ResponseEntity<String> error(HttpStatus status, String code, String message) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("error").put("code", code).put("message", message == null ? "" : message);
        return jsonResponse(status.value(), root.toString());
    }

    /**
     * Admin JSON response: never cached, never indexed, always {@code application/json}.
     * (The admin interceptor already sets these headers; they are repeated here because the
     * proxy contract must hold even if the controller is reached through another mapping setup.)
     */
    private static ResponseEntity<String> jsonResponse(int status, String body) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_ADMIN)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Robots-Tag", ROBOTS_NOINDEX)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? "" : body);
    }

    /* -------------------------------------------------------------- validation */

    private boolean csrfValid(HttpServletRequest request) {
        if (csrfSuppliedInQueryString(request)) {
            return false;
        }
        return adminAuth.csrfMatches(request.getSession(false), request.getHeader(CSRF_HEADER));
    }

    /** True when a CSRF token was supplied in the URL query string (never accepted). */
    static boolean csrfSuppliedInQueryString(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return queryString != null && CSRF_IN_QUERY_STRING.matcher(queryString).find();
    }

    /**
     * Reads at most {@link #MAX_BODY_BYTES} request bytes. Returns {@code null} when the body is
     * larger, so an oversized upload is discarded before it is buffered or parsed.
     */
    private static byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        try (InputStream input = request.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    return null;
                }
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        }
    }

    /**
     * Parses the body as a JSON object and keeps only allowlisted top-level fields. Returns
     * {@code null} for an empty body/invalid JSON/non-object JSON; the "empty body becomes {}"
     * case is handled explicitly so the optional sync body still works.
     */
    private ObjectNode filterJsonObject(byte[] body, Set<String> allowedFields) {
        ObjectNode filtered = objectMapper.createObjectNode();
        if (body == null || body.length == 0) {
            return filtered;
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(body);
        } catch (IOException ex) {
            return null;
        }
        if (parsed == null || !parsed.isObject()) {
            return null;
        }
        parsed.fields().forEachRemaining(entry -> {
            if (allowedFields.contains(entry.getKey())) {
                filtered.set(entry.getKey(), entry.getValue());
            }
        });
        return filtered;
    }

    /** Chat: a non-blank message of at most 4000 characters; {@code conversation_id} optional. */
    private static boolean isValidChatBody(ObjectNode body) {
        JsonNode message = body.get("message");
        if (message == null || !message.isTextual()) {
            return false;
        }
        String text = message.asText();
        if (text.isBlank() || text.length() > MAX_CHAT_MESSAGE_CHARS) {
            return false;
        }
        JsonNode conversationId = body.get("conversation_id");
        return conversationId == null || conversationId.isNull() || conversationId.isTextual();
    }

    /** Query: optional DSL clauses, each of the expected JSON kind when present. */
    private static boolean isValidQueryBody(ObjectNode body) {
        for (String arrayField : new String[]{"where", "group_by", "aggregate", "order_by"}) {
            JsonNode value = body.get(arrayField);
            if (value != null && !value.isNull() && !value.isArray()) {
                return false;
            }
        }
        JsonNode limit = body.get("limit");
        return limit == null || limit.isNull() || (limit.isIntegralNumber() && limit.canConvertToInt());
    }

    /** Summary: filters object (optional), documented focus value (optional), integer limit. */
    private static boolean isValidSummariseBody(ObjectNode body) {
        JsonNode filters = body.get("filters");
        if (filters != null && !filters.isNull() && !filters.isObject()) {
            return false;
        }
        JsonNode focus = body.get("focus");
        if (focus != null && !focus.isNull()
                && (!focus.isTextual() || !SUMMARISE_FOCI.contains(focus.asText()))) {
            return false;
        }
        JsonNode limit = body.get("limit");
        return limit == null || limit.isNull() || (limit.isIntegralNumber() && limit.canConvertToInt());
    }

    /** Sync: only an optional boolean {@code full}. An empty body means incremental. */
    private static boolean isValidSyncBody(ObjectNode body) {
        JsonNode full = body.get("full");
        return full == null || full.isNull() || full.isBoolean();
    }

    /** Accepts a missing content type for non-browser callers; otherwise it must be JSON. */
    private static boolean isJsonContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            String subtype = mediaType.getSubtype() == null
                    ? "" : mediaType.getSubtype().toLowerCase(Locale.ROOT);
            return "json".equals(subtype) || subtype.endsWith("+json");
        } catch (org.springframework.http.InvalidMediaTypeException ex) {
            return false;
        }
    }

    private boolean isJson(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            return objectMapper.readTree(body) != null;
        } catch (IOException ex) {
            return false;
        }
    }

    /** Clamps the audit limit to 1..200; a missing/invalid value falls back to the default. */
    static int clampAuditLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return DEFAULT_AUDIT_LIMIT;
        }
        int value;
        try {
            value = Integer.parseInt(rawLimit.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_AUDIT_LIMIT;
        }
        return Math.max(MIN_AUDIT_LIMIT, Math.min(MAX_AUDIT_LIMIT, value));
    }

    /**
     * Localised message from the companion bundle with a safe fallback, so a missing key can
     * never render a placeholder code or break a proxy response.
     */
    private String message(String key, String fallback) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, null, fallback, locale == null ? Locale.ENGLISH : locale);
    }
}
