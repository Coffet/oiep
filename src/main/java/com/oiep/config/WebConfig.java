package com.oiep.config;

import com.oiep.web.AdminAuthInterceptor;
import com.oiep.web.SessionCapacityFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Web configuration. Deliberately does not touch resource handlers: static assets are served
 * by Spring Boot's defaults from {@code classpath:/static} (css, js, fonts, img).
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    public static final String LOCALE_COOKIE_NAME = "OIEP_LANG";

    /**
     * CSP hashes for the two static inline resources in {@code fragments/head.jspf}: the
     * first-paint background rule and the tiny progressive-enhancement boot script. Hashes
     * (instead of {@code 'unsafe-inline'}) keep the identical page behaviour while making an
     * injected inline script/style unusable if an output-encoding bug ever appears.
     */
    static final String INLINE_STYLE_HASH = "sha256-ZvVLEVyZbnGqmPqLpAQyvwBta9bUV1tztEgocmTW3e8=";
    static final String INLINE_SCRIPT_HASH = "sha256-dNdlBplWR5bDZDI+YCKolkPPL27/gS/a7SNuPT2Lg0A=";

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'self'; object-src 'none'; "
                    + "frame-ancestors 'none'; form-action 'self'; "
                    + "img-src 'self' data:; font-src 'self'; "
                    + "style-src 'self' '" + INLINE_STYLE_HASH + "'; "
                    + "script-src 'self' '" + INLINE_SCRIPT_HASH + "'; connect-src 'self'";

    private final AdminAuthInterceptor adminAuthInterceptor;

    /**
     * Optional components that add CSP directives (for example an embedded widget origin).
     * Empty on a normal install, which keeps the shipped header unchanged.
     */
    private final List<CspContributor> cspContributors;

    public WebConfig(AdminAuthInterceptor adminAuthInterceptor,
                     ObjectProvider<CspContributor> cspContributors) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.cspContributors = cspContributors.orderedStream().toList();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");
    }

    /**
     * English unless the visitor explicitly chose Chinese (zh-HK) via /language/{lang}. The
     * locale cookie is marked {@code Secure} alongside the session cookie when the deployment
     * reports HTTPS, so it cannot leak over a plaintext downgrade either.
     */
    @Bean
    public LocaleResolver localeResolver(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        CookieLocaleResolver resolver = new CookieLocaleResolver(LOCALE_COOKIE_NAME);
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        resolver.setCookieHttpOnly(true);
        resolver.setCookieSecure(secureCookies);
        resolver.setCookieSameSite("Lax");
        // Cookie values are language tags: "en" / "zh-HK" (locale.toLanguageTag()).
        resolver.setLanguageTagCompliant(true);
        // A tampered/garbage cookie must fall back to English, not throw a 500.
        resolver.setRejectInvalidCookies(false);
        return resolver;
    }

    /** The base CSP value, exposed so tests can assert it matches the inline resources. */
    public static String contentSecurityPolicy() {
        return CONTENT_SECURITY_POLICY;
    }

    /**
     * The effective policy: the base policy plus any directives contributed by optional
     * components. With no contributors this returns exactly {@link #contentSecurityPolicy()}.
     */
    public static String contentSecurityPolicy(List<CspContributor> contributors) {
        if (contributors == null || contributors.isEmpty()) {
            return CONTENT_SECURITY_POLICY;
        }
        StringBuilder policy = new StringBuilder(CONTENT_SECURITY_POLICY);
        for (CspContributor contributor : contributors) {
            if (contributor == null) {
                continue;
            }
            List<String> directives = contributor.directives();
            if (directives == null) {
                continue;
            }
            for (String directive : directives) {
                if (directive == null || directive.isBlank()) {
                    continue;
                }
                policy.append("; ").append(directive.trim());
            }
        }
        return policy.toString();
    }

    /**
     * Baseline response-hardening layer. Inline script/style are authorised by their exact
     * SHA-256 hashes, so {@code 'unsafe-inline'} is not needed. HSTS is only emitted for
     * requests that reached the app over HTTPS (directly or via a trusted forwarding proxy),
     * because sending it on a plain-HTTP development instance would poison the browser for
     * that host.
     *
     * <p>Rendered HTML gets {@code Cache-Control: no-cache} and {@code Vary: Cookie}: the
     * language is chosen from the {@code OIEP_LANG} cookie and the page may include
     * session-scoped state, so shared caches must not serve one visitor's rendering to
     * another. Admin responses carry the stricter {@code no-store} header (set later by the
     * admin interceptor). Static assets keep their normal cacheability.</p>
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
                httpResponse.setHeader("Content-Security-Policy", contentSecurityPolicy(cspContributors));
                httpResponse.setHeader("Cross-Origin-Opener-Policy", "same-origin");
                httpResponse.setHeader("Cross-Origin-Resource-Policy", "same-origin");
                httpResponse.setHeader("X-Permitted-Cross-Domain-Policies", "none");
                if (request instanceof HttpServletRequest httpRequest) {
                    if (httpRequest.isSecure()) {
                        httpResponse.setHeader("Strict-Transport-Security",
                                "max-age=31536000; includeSubDomains");
                    }
                    String path = httpRequest.getRequestURI();
                    if (!isStaticAsset(path)) {
                        httpResponse.setHeader("Cache-Control", "no-cache");
                        httpResponse.addHeader("Vary", "Cookie");
                    }
                }
            }
            chain.doFilter(request, response);
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** True for the classpath-served static assets, which may be cached by shared caches. */
    static boolean isStaticAsset(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        String path = requestUri.toLowerCase(Locale.ROOT);
        return path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".woff2")
                || path.endsWith(".woff") || path.endsWith(".svg") || path.endsWith(".png")
                || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif")
                || path.endsWith(".ico") || path.endsWith(".txt") || path.endsWith(".map");
    }

    /**
     * Hard ceiling on anonymous session allocation. Kept as a registration bean (rather than
     * an auto-detected {@code Filter} bean) so the same instance is also registered as the
     * {@link HttpSessionListener} that maintains the live-session count.
     */
    @Bean
    public FilterRegistrationBean<SessionCapacityFilter> sessionCapacityFilter(
            @Value("${oiep.sessions.max-active:10000}") int maxActiveSessions) {
        FilterRegistrationBean<SessionCapacityFilter> registration =
                new FilterRegistrationBean<>(new SessionCapacityFilter(maxActiveSessions));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> sessionCapacityListener(
            FilterRegistrationBean<SessionCapacityFilter> sessionCapacityFilter) {
        return new ServletListenerRegistrationBean<>(sessionCapacityFilter.getFilter());
    }
}
