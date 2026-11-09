package com.oiep.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Locale switch. Persists the choice in a one-year {@code OIEP_LANG} cookie via
 * {@link LocaleResolver} and then redirects back to a safe relative URL.
 */
@Controller
public class LanguageController {

    private static final int MAX_RETURN_TO_LENGTH = 2000;

    private final LocaleResolver localeResolver;

    public LanguageController(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @GetMapping("/language/{lang}")
    public String switchLanguage(@PathVariable("lang") String lang,
                                 @RequestParam(name = "returnTo", required = false) String returnTo,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        localeResolver.setLocale(request, response, parseLocale(lang));
        return "redirect:" + safeReturnTo(returnTo);
    }

    /**
     * Case and separator tolerant: en / EN / en-GB map to English; zh, zh-hk, zh_HK,
     * zh-Hant-HK map to Chinese (Hong Kong). Unknown values fall back to English.
     */
    static Locale parseLocale(String rawLang) {
        if (rawLang == null) {
            return Locale.ENGLISH;
        }
        String normalized = rawLang.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.equals("zh") || normalized.startsWith("zh-")) {
            return Locale.forLanguageTag("zh-HK");
        }
        return Locale.ENGLISH;
    }

    /**
     * Only same-site relative paths are accepted: the value must start with exactly one "/".
     * Protocol-relative URLs ("//host"), absolute URLs, backslashes and CR/LF are rejected and
     * replaced with "/", so this cannot be used as an open redirect.
     */
    static String safeReturnTo(String returnTo) {
        if (returnTo == null) {
            return "/";
        }
        String value = returnTo.trim();
        if (value.isEmpty() || value.length() > MAX_RETURN_TO_LENGTH) {
            return "/";
        }
        if (value.charAt(0) != '/' || value.startsWith("//")) {
            return "/";
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 0x20 || character == 0x7F || character == '\\') {
                return "/";
            }
        }
        // Tomcat's sendRedirect rejects dot segments; returning a relative path containing
        // "." or ".." would surface as a 500. Only the path part needs checking.
        int queryStart = value.indexOf('?');
        String pathPart = queryStart >= 0 ? value.substring(0, queryStart) : value;
        for (String segment : pathPart.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return "/";
            }
        }
        return value;
    }
}
