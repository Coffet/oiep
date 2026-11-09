package com.oiep.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Replaces Spring Boot's whitelabel error page. The servlet container forwards errors to
 * {@code /error}; the original status and request path are read through
 * {@link ErrorAttributes}. Exception messages and stack traces are never exposed.
 */
@Controller
public class CustomErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public CustomErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> attributes;
        try {
            WebRequest webRequest = new ServletWebRequest(request);
            attributes = errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());
        } catch (RuntimeException ex) {
            attributes = Map.of();
        }

        int status = resolveStatus(request, attributes);
        String path = attributes.get("path") instanceof String value ? value : request.getRequestURI();
        response.setStatus(status);
        return GlobalModelAdvice.errorView(status, path);
    }

    private static int resolveStatus(HttpServletRequest request, Map<String, Object> attributes) {
        Object rawStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (rawStatus == null) {
            rawStatus = attributes.get("status");
        }
        if (rawStatus != null) {
            try {
                int status = Integer.parseInt(rawStatus.toString());
                if (status >= 400 && status <= 599) {
                    return status;
                }
            } catch (NumberFormatException ignored) {
                // fall through to 500
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
