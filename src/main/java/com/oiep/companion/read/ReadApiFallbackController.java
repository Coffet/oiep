package com.oiep.companion.read;

import com.oiep.companion.CompanionConditions;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON 404 fallback for the component-owned {@code /api/v1/**} namespace.
 *
 * <p>Only registered while the read API is enabled. Unknown API paths then fail as a small JSON
 * envelope (never as the application's HTML error page), so an API client always receives the
 * contract shape. With the component disabled the namespace is completely unclaimed and
 * callers see the normal 404 behaviour of an absent route.</p>
 *
 * <p>Like the read endpoint itself, the response content type is preset to JSON so no
 * {@code Accept} header can turn an unknown API path into an HTML error page or a 406.</p>
 */
@RestController
@Conditional(CompanionConditions.ReadApi.class)
public class ReadApiFallbackController {

    @RequestMapping("/api/v1/**")
    public ResponseEntity<ReadApiError> notFound(HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ReadApiHeaders.apply(response);
        return new ResponseEntity<>(ReadApiError.of("not_found"), HttpStatus.NOT_FOUND);
    }
}
