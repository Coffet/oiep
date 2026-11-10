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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redirect responses must be relative, so the browser resolves them against the public origin.
 * Absolute container redirects are built from the request {@code Host} (and, with forwarded
 * headers enabled, from client-influenced {@code X-Forwarded-Host}/{@code Forwarded} values);
 * a forged value would otherwise turn any redirect into a cross-host open redirect.
 *
 * <p>Uses a real embedded Tomcat because {@code server.tomcat.use-relative-redirects} is a
 * connector/context behaviour that MockMvc cannot exercise.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-redirect-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password"
})
class RedirectHardeningTests {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Test
    void configUsesRelativeRedirects() {
        assertThat(environment.getProperty("server.tomcat.use-relative-redirects")).isEqualTo("true");
    }

    @Test
    void languageRedirectIsRelativeEvenWithForgedForwardedHeaders() throws Exception {
        HttpResponse<String> response = get("/language/en?returnTo=/browse",
                "X-Forwarded-Host", "evil.example",
                "X-Forwarded-Proto", "https",
                "Forwarded", "host=evil.example;proto=https",
                "CF-Connecting-IP", "198.51.100.7");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response)).isEqualTo("/browse");
    }

    @Test
    void adminLoginRedirectIsRelativeEvenWithForgedForwardedHeaders() throws Exception {
        HttpResponse<String> response = get("/admin",
                "X-Forwarded-Host", "evil.example",
                "X-Forwarded-Proto", "https");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response)).isEqualTo("/admin/login");
    }

    @Test
    void errorRedirectsKeepTheRelativeLocationHeader() throws Exception {
        HttpResponse<String> response = get("/language/zh-HK?returnTo=//evil.example");
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response)).isEqualTo("/");
    }

    private HttpResponse<String> get(String path, String... extraHeaders) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .GET();
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            builder.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }
}
