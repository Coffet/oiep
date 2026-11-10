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
 * End-to-end proof that the session-cap filter and its {@code HttpSessionListener} are wired
 * into the real container (MockMvc does not fire container listeners). With a cap of one, the
 * second cookie-less session-creating request must receive 503 instead of creating a session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-session-cap-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.sessions.max-active=1"
})
class SessionCapacityEndToEndTests {

    @LocalServerPort
    private int port;

    @Test
    void secondCookieLessSessionRequestIsRefusedOnceTheCapIsReached() throws Exception {
        HttpResponse<String> first = get("/admin/login");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().allValues("Set-Cookie").toString()).contains("JSESSIONID=");

        HttpResponse<String> second = get("/admin/login");
        assertThat(second.statusCode())
                .as("the live HttpSessionListener count must make the cap effective")
                .isEqualTo(503);
        assertThat(second.headers().allValues("Set-Cookie").toString())
                .as("a refused request must not allocate a session")
                .doesNotContain("JSESSIONID=");

        // Non-session pages remain available while the cap is reached.
        assertThat(get("/browse").statusCode()).isEqualTo(200);
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
