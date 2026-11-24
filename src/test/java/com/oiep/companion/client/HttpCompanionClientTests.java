package com.oiep.companion.client;

import com.oiep.companion.CompanionProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boundary tests for the server-side companion HTTP client: bounded streaming reads and
 * credential-reflection defence (a companion response must never be able to push the
 * operator token into the browser through the proxy).
 */
class HttpCompanionClientTests {

    private static final String TOKEN = "test-companion-admin-token-0123456789abcdef";

    private HttpServer server;
    private HttpCompanionClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        CompanionProperties properties = new CompanionProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getAdminHelper().setEnabled(true);
        properties.getAdminHelper().setToken(TOKEN);
        client = new HttpCompanionClient(properties);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void respond(byte[] body) {
        server.createContext("/v1/admin/health", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    @Test
    void normalJsonResponseIsReturned() {
        respond("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        CompanionResponse response = client.get("/v1/admin/health");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void oversizedResponseIsRejectedWithoutUnboundedBuffering() {
        byte[] oversized = new byte[2 * 1024 * 1024 + 64 * 1024];
        java.util.Arrays.fill(oversized, (byte) 'a');
        respond(oversized);
        assertThatThrownBy(() -> client.get("/v1/admin/health"))
                .isInstanceOf(CompanionUnavailableException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void responseEchoingTheOperatorTokenIsRejected() {
        respond(("{\"detail\":\"leaked " + TOKEN + "\"}").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> client.get("/v1/admin/health"))
                .isInstanceOf(CompanionUnavailableException.class);
    }

    @Test
    void clientRejectsRemoteHttpUnlessExplicitlyAllowed() {
        CompanionProperties properties = new CompanionProperties();
        properties.setBaseUrl("http://ai.example.org");
        properties.getAdminHelper().setToken(TOKEN);

        assertThatThrownBy(() -> new HttpCompanionClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        properties.getAdminHelper().setAllowInsecureHttp(true);
        assertThat(new HttpCompanionClient(properties)).isNotNull();
    }
}
