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
 * Counterpart to {@link TrustedProxyHeaderEndToEndTests}: when the immediate peer is not a
 * trusted proxy, the native RemoteIpValve ignores {@code CF-Connecting-IP}, and the resolver's
 * peer-equality check must also ignore it. A spoofed header therefore cannot create fresh
 * throttle buckets on a directly-exposed instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-untrusted-proxy-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password",
        "oiep.security.trust-cloudflare-connecting-ip=true",
        // Only TEST-NET-1 is "internal": the loopback test client is therefore an untrusted
        // peer, which simulates a directly exposed instance.
        "server.tomcat.remoteip.internal-proxies=192\\.0\\.2\\.\\d+"
})
class UntrustedProxyHeaderEndToEndTests {

    @LocalServerPort
    private int port;

    @Test
    void spoofedCloudflareHeaderFromUntrustedPeerCannotCreateFreshBuckets() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            statuses.add(postReport("Untrusted CF Co " + i, "CF-Connecting-IP", "198.51.100." + i));
        }
        assertThat(statuses)
                .as("spoofed CF-Connecting-IP must not bypass the per-client submission limit")
                .containsSubsequence(302, 302, 302, 302, 302, 302, 302, 302, 429, 429);
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
