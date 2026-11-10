package com.oiep.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of the forwarded-header fix in the real container: the native
 * RemoteIpValve trusts {@code CF-Connecting-IP} from the loopback peer (the trusted-proxy
 * hop in this test) and the submission limiter tracks each Cloudflare client separately.
 * Rotating {@code X-Forwarded-For} is inert.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-trusted-proxy-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.security.trust-cloudflare-connecting-ip=true"
})
class TrustedProxyHeaderEndToEndTests {

    @LocalServerPort
    private int port;

    @Test
    void cloudflareClientAddressesGetIndependentSubmissionBuckets() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            statuses.add(postReport("Trusted CF Co " + i, "CF-Connecting-IP", "198.51.100." + i));
        }
        assertThat(statuses).as("each trusted CF client has its own 8-post bucket").containsOnly(302);
    }

    @Test
    void xForwardedForRotationDoesNotCreateBuckets() throws Exception {
        // Ten submissions from one apparent client (no CF header -> socket address) with a
        // rotating spoofed X-Forwarded-For: the limiter must count them as one client.
        List<Integer> statuses = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            statuses.add(postReport("Rotating XFF Co " + i, "X-Forwarded-For", "203.0.113." + i));
        }
        assertThat(statuses).containsSubsequence(302, 302, 302, 302, 302, 302, 302, 302, 429, 429);
    }

    private int postReport(String company, String headerName, String headerValue) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String body = "company=" + enc(company)
                + "&role=Engineer"
                + "&round=technical"
                + "&workMode=hybrid"
                + "&outcome=offer"
                + "&heardBack=week"
                + "&story=" + enc("A sufficiently long and detailed story about the interview process.")
                + "&confirm=true";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/reports"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(headerName, headerValue)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
