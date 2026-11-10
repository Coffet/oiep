package com.oiep.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the operator opts in (the shipped Docker/Cloudflare deployment does), the session
 * cookie must carry the {@code Secure} attribute alongside {@code HttpOnly} and
 * {@code SameSite=Lax}. This needs a real embedded Tomcat, because the servlet container
 * (not MockMvc) is what attaches the session cookie to the response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-secure-cookie-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "server.servlet.session.cookie.secure=true"
})
class SecureSessionCookieTests {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Test
    void sessionCookieIsSecureHttpOnlyAndSameSiteLax() throws Exception {
        assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/admin/login"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String setCookies = response.headers().allValues("Set-Cookie").stream()
                .collect(Collectors.joining("\n"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(setCookies).contains("JSESSIONID=");
        assertThat(setCookies).contains("Secure");
        assertThat(setCookies).contains("HttpOnly");
        assertThat(setCookies).contains("SameSite=Lax");
    }

    @Test
    void localeCookieIsAlsoSecureWhenHttpsIsConfigured() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/language/zh-HK?returnTo=/browse"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String localeCookie = response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("OIEP_LANG="))
                .findFirst()
                .orElse("");
        assertThat(localeCookie).contains("OIEP_LANG=");
        assertThat(localeCookie).contains("Secure");
        assertThat(localeCookie).contains("HttpOnly");
        assertThat(localeCookie).contains("SameSite=Lax");
    }
}
