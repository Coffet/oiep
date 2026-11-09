package com.oiep.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.oiep.web.AdminAuthInterceptor;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Locale;

/**
 * Web configuration. Deliberately does not touch resource handlers: static assets are served
 * by Spring Boot's defaults from {@code classpath:/static} (css, js, fonts, img).
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    public static final String LOCALE_COOKIE_NAME = "OIEP_LANG";

    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");
    }

    /** English unless the visitor explicitly chose Chinese (zh-HK) via /language/{lang}. */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(LOCALE_COOKIE_NAME);
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        resolver.setCookieHttpOnly(true);
        resolver.setCookieSameSite("Lax");
        // Cookie values are language tags: "en" / "zh-HK" (locale.toLanguageTag()).
        resolver.setLanguageTagCompliant(true);
        // A tampered/garbage cookie must fall back to English, not throw a 500.
        resolver.setRejectInvalidCookies(false);
        return resolver;
    }

    /**
     * Small baseline response-hardening layer. The inline style/script allowances are needed
     * by the paper texture and the progressive-enhancement boot class; they can be replaced
     * with nonces later without changing the page structure.
     */
    @Bean
    public FilterRegistrationBean<Filter> securityHeadersFilter() {
        Filter filter = (request, response, chain) -> {
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader("X-Content-Type-Options", "nosniff");
                httpResponse.setHeader("X-Frame-Options", "DENY");
                httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                httpResponse.setHeader("Permissions-Policy",
                        "camera=(), geolocation=(), microphone=(), payment=()");
                httpResponse.setHeader("Content-Security-Policy",
                        "default-src 'self'; base-uri 'self'; object-src 'none'; "
                                + "frame-ancestors 'none'; form-action 'self'; "
                                + "img-src 'self' data:; font-src 'self'; "
                                + "style-src 'self' 'unsafe-inline'; "
                                + "script-src 'self' 'unsafe-inline'; connect-src 'self'");
            }
            chain.doFilter(request, response);
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
