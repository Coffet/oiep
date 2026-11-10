package com.oiep.companion.user;

import com.oiep.config.WebConfig;
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
 * Shipped default configuration, verified on the real rendered page: with the component
 * disabled the landing page is exactly the pre-companion page. MockMvc cannot prove this
 * because it does not render JSPs, hence a real embedded Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-companion-user-disabled-e2e-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password"
})
class CompanionUserHelperDisabledEndToEndTests {

    @LocalServerPort
    private int port;

    @Test
    void landingPageHasNoCompanionMarkupAndNoFrameDirective() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        String html = response.body();
        // Sanity: the page really rendered (so the negative assertions mean something).
        assertThat(html).contains("id=\"debrief-form\"");
        assertThat(html).doesNotContain("data-companion-helper");
        assertThat(html).doesNotContain("companion-helper__iframe");
        assertThat(html).doesNotContain("/js/companion.js");
        assertThat(html).doesNotContain("/css/companion.css");
        assertThat(html).doesNotContain("ai.example.org");

        String csp = response.headers().firstValue("Content-Security-Policy").orElse("");
        assertThat(csp).doesNotContain("frame-src");
        // Disabled means the header is byte-identical to the shipped base policy.
        assertThat(csp).isEqualTo(WebConfig.contentSecurityPolicy());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
