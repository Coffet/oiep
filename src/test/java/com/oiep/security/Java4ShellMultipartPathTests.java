package com.oiep.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.multipart.MultipartResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the Java4Shell (CVE-2026-114938) pre-auth framework path.
 *
 * <p>Tomcat's multipart part-header parser decodes RFC 2047 encoded words with
 * {@code java.util.Base64}. Because Spring's multipart resolver made {@code DispatcherServlet}
 * parse parts before handler mapping and authentication, an unauthenticated
 * {@code POST multipart/form-data} with a malformed encoded word in
 * {@code Content-Disposition} reached the vulnerable HotSpot Base64 decode intrinsic entry
 * point (CVE-2026-114938). Multipart parsing is disabled in {@code application.properties}, so
 * no part headers are decoded and the request is an ordinary mapping result instead of a 500.</p>
 *
 * <p>This is a real-socket test on purpose: MockMvc never exercises Tomcat's part parser, so it
 * cannot catch a regression in this path.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/oiep-java4shell-multipart-${random.uuid}.db?busy_timeout=5000&journal_mode=WAL",
        "oiep.demo-data=false",
        "oiep.admin.username=test-admin",
        "oiep.admin.password=test-password"
})
class Java4ShellMultipartPathTests {

    private static final String BOUNDARY = "----OIEPJAVA4SHELLBOUNDARY";
    /** JDK 17 trigger shape: the decoder mishandles this padded tail on the intrinsic path. */
    private static final String TRIGGER = "A".repeat(128) + "=";

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext context;

    @Test
    void multipartParsingIsDisabled() {
        assertThat(environment.getProperty("spring.servlet.multipart.enabled")).isEqualTo("false");
        assertThat(context.getBeansOfType(MultipartResolver.class)).isEmpty();
    }

    @Test
    void malformedEncodedWordInMultipartHeaderNeverReachesAParser() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        for (String path : new String[]{"/definitely-not-a-route", "/reports", "/"}) {
            HttpResponse<String> response = postMalformedMultipart(client, path);
            // Before the fix this was 500 from java.util.Base64$Decoder.decode0, raised while
            // Tomcat parsed the part header, i.e. before routing and authentication.
            assertThat(response.statusCode())
                    .as("multipart request to %s must not hit the framework Base64 decode path", path)
                    .isNotEqualTo(500);
        }

        HttpResponse<String> home = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(home.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> postMalformedMultipart(HttpClient client, String path)
            throws Exception {
        String body = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"f\"; filename=\"=?UTF-8?B?"
                + TRIGGER + "?=\"\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "x\r\n"
                + "--" + BOUNDARY + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.US_ASCII)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
