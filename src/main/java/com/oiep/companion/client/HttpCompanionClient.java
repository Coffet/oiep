package com.oiep.companion.client;

import com.oiep.companion.CompanionProperties;
import com.oiep.companion.CompanionSettingsSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link CompanionClient} over the JDK HTTP client (no extra dependency).
 *
 * <p>Security properties: the operator token only ever travels in the {@code Authorization}
 * header from the server process; requests are restricted to the fixed
 * {@code /v1/admin/...} route prefix; redirects are not followed; the response body is
 * bounded; and neither the token nor a request/response body is ever logged. Transport
 * failures are converted to {@link CompanionUnavailableException} so the web layer can
 * return a friendly {@code 502} instead of leaking internals.</p>
 */
@Component
@org.springframework.context.annotation.Conditional(com.oiep.companion.CompanionConditions.AdminHelper.class)
public class HttpCompanionClient implements CompanionClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCompanionClient.class);

    private static final String ADMIN_PATH_PREFIX = "/v1/admin/";
    private static final String USER_AGENT = "oiep-companion/1.0";
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PATH_LENGTH = 512;

    private final HttpClient httpClient;
    private final String serviceOrigin;
    private final String adminToken;
    private final Duration requestTimeout;

    public HttpCompanionClient(CompanionProperties properties) {
        String origin = properties.serviceOrigin();
        String token = CompanionSettingsSupport.trimToEmpty(properties.getAdminHelper().getToken());
        if (origin.isEmpty() || token.isEmpty()) {
            throw new IllegalStateException(
                    "The AI companion admin helper requires a valid OIEP_COMPANION_BASE_URL and token");
        }
        this.serviceOrigin = origin;
        this.adminToken = token;
        this.requestTimeout = Duration.ofMillis(Math.max(500, properties.getHttp().getRequestTimeoutMs()));
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getHttp().getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        log.info("AI companion admin client configured for origin={}", origin);
    }

    @Override
    public CompanionResponse get(String pathAndQuery) {
        return send("GET", requireAdminPath(pathAndQuery, true), null);
    }

    @Override
    public CompanionResponse postJson(String path, String jsonBody) {
        return send("POST", requireAdminPath(path, false), jsonBody == null ? "{}" : jsonBody);
    }

    private CompanionResponse send(String method, String pathAndQuery, String jsonBody) {
        URI uri;
        try {
            uri = URI.create(serviceOrigin + pathAndQuery);
        } catch (IllegalArgumentException ex) {
            throw new CompanionUnavailableException("The companion request path is invalid", ex);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + adminToken)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        HttpResponse<InputStream> response;
        String body;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            body = readBoundedBody(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CompanionUnavailableException("The companion request was interrupted", ex);
        } catch (CompanionUnavailableException ex) {
            // Keep the specific (already sanitised) reason, e.g. "response was too large".
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new CompanionUnavailableException("The companion service could not be reached", ex);
        }
        if (body.contains(adminToken)) {
            // Defence in depth for a compromised or buggy companion: an operator credential
            // must never be reflected into the browser through the proxy.
            log.warn("AI companion response contained the operator credential; refusing to pass it through");
            throw new CompanionUnavailableException("The companion response contained a credential");
        }
        if (response.statusCode() >= 500) {
            log.warn("AI companion request failed path={} status={}", pathAndQuery, response.statusCode());
        }
        return new CompanionResponse(response.statusCode(), body);
    }

    /**
     * Reads the response body while enforcing the byte ceiling.
     *
     * <p>The stream is consumed incrementally, so a hostile or misconfigured companion cannot
     * make the OIEP process buffer an arbitrarily large body before the size check (the old
     * implementation buffered the whole response and compared a character count).</p>
     */
    private String readBoundedBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new CompanionUnavailableException("The companion response was too large");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Only fixed operator route paths may be requested. This is a defence in depth: the web
     * layer already builds the paths from a constant list, but the client refuses anything
     * that is not clearly an admin API path.
     */
    private static String requireAdminPath(String pathAndQuery, boolean allowQuery) {
        String candidate = CompanionSettingsSupport.trimToEmpty(pathAndQuery);
        if (candidate.length() > MAX_PATH_LENGTH
                || !candidate.startsWith(ADMIN_PATH_PREFIX)
                || candidate.contains("..")
                || candidate.contains("\\")
                || candidate.contains("://")
                || candidate.contains("#")) {
            throw new CompanionUnavailableException("The companion request path is not an operator route");
        }
        int queryIndex = candidate.indexOf('?');
        if (queryIndex >= 0 && !allowQuery) {
            throw new CompanionUnavailableException("The companion request path must not contain a query");
        }
        for (int i = 0; i < candidate.length(); i++) {
            char character = candidate.charAt(i);
            if (character < 0x20 || character == 0x7F) {
                throw new CompanionUnavailableException("The companion request path contains control characters");
            }
        }
        return candidate;
    }
}
