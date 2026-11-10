package com.oiep.companion;

import com.oiep.companion.client.CompanionClient;
import com.oiep.companion.client.CompanionResponse;
import com.oiep.companion.client.CompanionUnavailableException;
import com.oiep.companion.client.HttpCompanionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The companion client is the only place where the operator token leaves the OIEP process.
 * Tests therefore focus on route restrictions, header handling and failure mapping.
 */
class CompanionHttpClientTests {

    private static final String ADMIN_TOKEN = "test-admin-token-0123456789abcdef";

    private StubCompanionServer server;
    private CompanionClient client;
    private CompanionProperties properties;

    @BeforeEach
    void setUp() {
        server = new StubCompanionServer();
        properties = new CompanionProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(server.baseUrl());
        properties.getAdminHelper().setEnabled(true);
        properties.getAdminHelper().setToken(ADMIN_TOKEN);
        properties.getHttp().setConnectTimeoutMs(1000);
        properties.getHttp().setRequestTimeoutMs(2000);
        client = new HttpCompanionClient(properties);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void getSendsTheBearerTokenToTheAdminRouteAndReturnsTheBody() {
        server.enqueue(200, "{\"corpus\":{\"experiences\":7}}");
        CompanionResponse response = client.get("/v1/admin/health");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("experiences");
        StubCompanionServer.RecordedRequest request = server.lastRequest();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/v1/admin/health");
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + ADMIN_TOKEN);
        assertThat(request.header("Accept")).isEqualTo("application/json");
    }

    @Test
    void postForwardsJsonAndKeepsTheTokenInTheHeader() {
        server.enqueue(200, "{\"answer\":\"ok\"}");
        CompanionResponse response = client.postJson("/v1/admin/chat", "{\"message\":\"hi\"}");

        assertThat(response.status()).isEqualTo(200);
        StubCompanionServer.RecordedRequest request = server.lastRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.body()).isEqualTo("{\"message\":\"hi\"}");
        assertThat(request.header("Content-Type")).startsWith("application/json");
    }

    @Test
    void getSupportsAnAllowlistedQueryString() {
        server.enqueue(200, "{\"entries\":[]}");
        client.get("/v1/admin/audit?limit=10");
        assertThat(server.lastRequest().query()).isEqualTo("limit=10");
    }

    @Test
    void nonAdminPathsAreRefusedLocally() {
        assertThatThrownBy(() -> client.get("/v1/user/sessions"))
                .isInstanceOf(CompanionUnavailableException.class)
                .hasMessageContaining("operator route");
        assertThatThrownBy(() -> client.get("/v1/admin/../user/sessions"))
                .isInstanceOf(CompanionUnavailableException.class);
        assertThatThrownBy(() -> client.get("/healthz"))
                .isInstanceOf(CompanionUnavailableException.class);
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void transportFailureBecomesACompanionUnavailableException() {
        CompanionProperties unreachable = new CompanionProperties();
        unreachable.setEnabled(true);
        unreachable.setBaseUrl("http://127.0.0.1:1");
        unreachable.getAdminHelper().setToken(ADMIN_TOKEN);
        unreachable.getHttp().setConnectTimeoutMs(500);
        unreachable.getHttp().setRequestTimeoutMs(500);
        CompanionClient failing = new HttpCompanionClient(unreachable);

        assertThatThrownBy(() -> failing.get("/v1/admin/health"))
                .isInstanceOf(CompanionUnavailableException.class)
                .hasMessageNotContaining(ADMIN_TOKEN);
    }

    @Test
    void oversizedResponseBodiesAreRejected() {
        server.enqueue(200, "x".repeat(2 * 1024 * 1024 + 10));
        assertThatThrownBy(() -> client.get("/v1/admin/health"))
                .isInstanceOf(CompanionUnavailableException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void clientUsesHttp11BecauseTheCompanionDoesNotSpeakH2c() {
        // Regression: the JDK client defaults to an h2c upgrade attempt on plaintext
        // connections; uvicorn (the companion) does not support that and answers with an
        // unparsable 400, which surfaced as a 502 in the admin proxy during real E2E.
        server.enqueue(200, "{\"status\":\"ok\"}");
        client.get("/v1/admin/health");
        assertThat(server.lastRequest().protocol()).isEqualTo("HTTP/1.1");
    }

    @Test
    void errorStatusesAreReturnedToTheCallerForMapping() {
        server.enqueue(503, "{\"error\":{\"code\":\"model_busy\"}}");
        CompanionResponse response = client.get("/v1/admin/health");
        assertThat(response.status()).isEqualTo(503);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.body()).contains("model_busy");
    }
}
