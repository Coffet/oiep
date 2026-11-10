package com.oiep.web;

import com.oiep.web.ui.PageExtension;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

import java.time.Year;
import java.util.List;
import java.util.Locale;

/**
 * Model attributes available to every view and the mapping from "not found" to the styled
 * error page.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    /**
     * Optional component-registered view fragments. Empty when no optional component is
     * active, so the shipped pages render exactly the same markup.
     */
    private final List<PageExtension> pageExtensions;

    public GlobalModelAdvice(ObjectProvider<PageExtension> pageExtensions) {
        this.pageExtensions = pageExtensions.orderedStream().toList();
    }

    /** Fragments that optional components want included on a given page slot. */
    @ModelAttribute("pageExtensions")
    public List<PageExtension> pageExtensions() {
        return pageExtensions;
    }

    /**
     * Language tag for the {@code <html lang>} attribute: "en" or "zh-HK". Only these two
     * languages exist in this phase; anything else falls back to English.
     */
    @ModelAttribute("pageLang")
    public String pageLang(Locale locale) {
        return toPageLang(locale);
    }

    private static String toPageLang(Locale locale) {
        if (locale == null) {
            return "en";
        }
        return "zh".equalsIgnoreCase(locale.getLanguage()) ? "zh-HK" : "en";
    }

    @ModelAttribute("currentYear")
    public int currentYear() {
        return Year.now().getValue();
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ModelAndView handleReportNotFound(ReportNotFoundException exception, HttpServletRequest request) {
        return errorView(HttpStatus.NOT_FOUND.value(), request.getRequestURI());
    }

    /**
     * Builds the styled error view for a given status. Shared with
     * {@link CustomErrorController}.
     */
    static ModelAndView errorView(int rawStatus, String path) {
        HttpStatus status = resolveStatus(rawStatus);
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("error", status.getReasonPhrase());
        modelAndView.addObject("path", path == null ? "" : path);
        // Never bounce the language switch back to the broken URL.
        modelAndView.addObject("currentUrl", "/");
        modelAndView.addObject("activeNav", "");
        // @ModelAttribute methods do not run for @ExceptionHandler results, so supply the
        // language needed by <html lang> here as well (WCAG 3.1.1).
        modelAndView.addObject("pageLang", toPageLang(LocaleContextHolder.getLocale()));
        modelAndView.addObject("currentYear", Year.now().getValue());
        return modelAndView;
    }

    private static HttpStatus resolveStatus(int rawStatus) {
        if (rawStatus < 400 || rawStatus > 599) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatus status = HttpStatus.resolve(rawStatus);
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
