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
 * A deployment with no admin password must serve the disabled notice without allocating a
 * session (the pre-fix behaviour allocated one per visit).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-admin-disabled-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password="
})
class AdminDisabledSessionEndToEndTests {

    @LocalServerPort
    private int port;

    @Test
    void disabledAdminLoginPageAllocatesNoSession() throws Exception {
        HttpResponse<String> response = get("/admin/login");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Admin access is not configured");
        assertThat(response.body()).doesNotContain("OIEP_ADMIN_PASSWORD");
        assertThat(response.headers().allValues("Set-Cookie").toString())
                .as("the disabled admin page must not create a session")
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
