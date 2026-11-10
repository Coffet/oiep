package com.oiep.security;

import com.oiep.IntegrationTestSupport;
import com.oiep.config.WebConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression cover for the response-hardening layer: the CSP must authorise exactly the two
 * static inline resources in {@code head.jspf} by hash (never {@code 'unsafe-inline'}), and
 * HSTS must only be sent for requests that reached the app over HTTPS.
 */
class SecurityHeadersIntegrationTests extends IntegrationTestSupport {

    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern INLINE_STYLE = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);

    @Test
    void contentSecurityPolicyMatchesTheInlineResourcesWithoutUnsafeInline() throws Exception {
        String csp = WebConfig.contentSecurityPolicy();

        assertThat(csp).doesNotContain("unsafe-inline");
        assertThat(csp).contains("default-src 'self'");
        assertThat(csp).contains("frame-ancestors 'none'");
        assertThat(csp).contains("object-src 'none'");
        // CSP hash sources MUST be single-quoted, otherwise browsers discard the token and
        // (without 'unsafe-inline') refuse the inline resource. Assert the quoted form.
        String scriptHash = inlineHash(INLINE_SCRIPT, "script");
        String styleHash = inlineHash(INLINE_STYLE, "style");
        assertThat(csp).contains("script-src 'self' '" + scriptHash + "'");
        assertThat(csp).contains("style-src 'self' '" + styleHash + "'");
        assertThat(csp).doesNotContain("script-src 'self' " + scriptHash);
        assertThat(csp).doesNotContain("style-src 'self' " + styleHash);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", csp));
    }

    @Test
    void otherBaselineHeadersArePresent() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("X-Permitted-Cross-Domain-Policies", "none"));
    }

    @Test
    void hstsIsSentOnlyOverHttps() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        mockMvc.perform(get("/").secure(true))
                .andExpect(header().string("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains"));
    }

    @Test
    void htmlResponsesAreNotSharedCacheable() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("Vary", org.hamcrest.Matchers.containsString("Cookie")));
    }

    @Test
    void headersArePresentOnTheErrorPathToo() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(header().string("Content-Security-Policy", WebConfig.contentSecurityPolicy()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    private static String inlineHash(Pattern pattern, String kind) throws Exception {
        Path head = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "fragments", "head.jspf");
        assertThat(head).as("head.jspf must be readable from the project base directory").exists();
        String content = Files.readString(head, StandardCharsets.UTF_8);
        Matcher matcher = pattern.matcher(content);
        assertThat(matcher.find()).as("inline %s block must exist in head.jspf", kind).isTrue();
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(matcher.group(1).getBytes(StandardCharsets.UTF_8));
        return "sha256-" + Base64.getEncoder().encodeToString(digest);
    }
}
