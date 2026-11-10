package com.oiep.companion.read;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns read API failures into the fixed JSON error envelope instead of the application's HTML
 * error page. Scoped to {@link ReadApiController}, so no other OIEP route is affected.
 *
 * <p>Only fixed codes are exposed. Request values (token, cursor, {@code updated_since},
 * page-size input) and exception messages are never included in a response or a log line.</p>
 */
@RestControllerAdvice(assignableTypes = ReadApiController.class)
public class ReadApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReadApiExceptionHandler.class);

    @ExceptionHandler(ReadApiRequestException.class)
    public ResponseEntity<ReadApiError> handleRequestException(
            ReadApiRequestException exception, HttpServletResponse response) {
        return error(exception.status(), exception.code(), response);
    }

    /**
     * Last-resort handler: any unexpected failure inside the read controller becomes a generic
     * JSON 500 (no stack trace, no message), keeping the API contract valid for the caller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ReadApiError> handleUnexpected(
            Exception exception, HttpServletResponse response) {
        log.error("AI companion read API request failed: {}", exception.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", response);
    }

    private static ResponseEntity<ReadApiError> error(
            HttpStatus status, String code, HttpServletResponse response) {
        // JSON is preset for every error, whatever the request's Accept header says: this
        // namespace must never render the application's HTML error page or a 406.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ReadApiHeaders.apply(response);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return builder.body(ReadApiError.of(code));
    }
}
