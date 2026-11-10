package com.oiep.companion.read;

import com.oiep.companion.CompanionConditions;
import com.oiep.web.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-only experiences endpoint consumed by oiep-backendAI's admin corpus sync
 * ({@code oiep-read-api/v1}).
 *
 * <p>{@code GET /api/v1/experiences} requires
 * {@code Authorization: Bearer <OIEP_COMPANION_READ_TOKEN>}; it never reads a session, a cookie
 * or a browser header, and it is the only route that accepts that token. The endpoint exists
 * only when the component and the read API are enabled with a usable token: with the default
 * configuration the route is absent (404), exactly like any other unknown page.</p>
 *
 * <p>Every request (including failed authentication attempts) is throttled per client before
 * the token is checked, and the token comparison is constant time; together they make online
 * guessing impractical and keep unauthenticated 401 traffic from being free.</p>
 *
 * <p>Responses always carry {@code Cache-Control: no-store} and {@code X-Robots-Tag: noindex}
 * and never CORS headers. Errors use the fixed JSON envelope
 * {@code {"error":{"code":"..."}}}; neither the token nor any query value is echoed back.</p>
 *
 * <p>The response content type is preset to JSON before anything else happens, unconditionally
 * of the request's {@code Accept} header. This endpoint is for machines: a browser-style
 * {@code Accept: text/html} (or any other unacceptable media type) must never reach OIEP's HTML
 * error page or a {@code 406} — every success and error response from this namespace is
 * JSON.</p>
 */
@RestController
@Conditional(CompanionConditions.ReadApi.class)
public class ReadApiController {

    /** Fixed public path of the read endpoint. */
    public static final String EXPERIENCES_PATH = "/api/v1/experiences";

    private final ReadApiAuth auth;
    private final ReadApiService service;
    private final ReadApiRateLimiter rateLimiter;
    private final ClientAddressResolver clientAddressResolver;

    public ReadApiController(ReadApiAuth auth, ReadApiService service,
                             ReadApiRateLimiter rateLimiter,
                             ClientAddressResolver clientAddressResolver) {
        this.auth = auth;
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.clientAddressResolver = clientAddressResolver;
    }

    @GetMapping(EXPERIENCES_PATH)
    public ResponseEntity<ExperiencesPage> experiences(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "updated_since", required = false) String updatedSince,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "page_size", required = false) String pageSize,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Preset a concrete content type: Spring then skips Accept negotiation, so a browser or
        // hostile Accept header can never fall back to the HTML error page.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ReadApiHeaders.apply(response);
        String client = clientAddressResolver.resolve(request);
        if (!rateLimiter.allow(client)) {
            throw ReadApiRequestException.rateLimited((int) rateLimiter.retryAfterSeconds());
        }
        if (!auth.isAuthorized(authorization)) {
            throw ReadApiRequestException.unauthorized();
        }
        return ResponseEntity.ok(service.page(updatedSince, cursor, pageSize));
    }
}
