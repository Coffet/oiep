package com.oiep.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of the login-page throttle: once the per-client budget is exhausted the
 * page returns 429 <em>and does not allocate a session</em>, which is what bounds the
 * anonymous session-exhaustion primitive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-login-throttle-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.sessions.max-active=10000"
})
class LoginPageThrottleEndToEndTests {

    @LocalServerPort
    private int port;

    @Test
    void throttledLoginPageAllocatesNoSession() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertThat(get("/admin/login").statusCode()).isEqualTo(200);
        }
        HttpResponse<String> throttled = get("/admin/login");
        assertThat(throttled.statusCode()).isEqualTo(429);
        assertThat(throttled.headers().allValues("Set-Cookie").toString())
                .as("a throttled login page must not create a JSESSIONID")
                .doesNotContain("JSESSIONID=");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
