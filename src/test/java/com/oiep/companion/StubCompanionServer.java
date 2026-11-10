package com.oiep.companion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-process stand-in for the oiep-backendAI service, used by component tests.
 *
 * <p>No mocking framework and no network: the server binds to loopback on an ephemeral port.
 * Requests are recorded (method, path, query, headers, body) and responses can be queued per
 * test; by default it answers {@code 200 {"ok":true}}. This is test-only code and lives with
 * the optional component, so removing the component removes it too.</p>
 */
public final class StubCompanionServer implements AutoCloseable {

    private final HttpServer server;
    private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentLinkedDeque<StubResponse> queued = new ConcurrentLinkedDeque<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile StubResponse defaultResponse = new StubResponse(200, "{\"ok\":true}", "application/json");

    public StubCompanionServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the companion test server", ex);
        }
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    /** Base URL (origin) tests should configure as {@code oiep.companion.base-url}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void enqueue(int status, String body) {
        queued.add(new StubResponse(status, body, "application/json"));
    }

    public void enqueue(int status, String body, String contentType) {
        queued.add(new StubResponse(status, body, contentType));
    }

    public void setDefaultResponse(int status, String body) {
        defaultResponse = new StubResponse(status, body, "application/json");
    }

    public List<RecordedRequest> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    public RecordedRequest lastRequest() {
        synchronized (requests) {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }
    }

    public int requestCount() {
        return requestCount.get();
    }

    public void reset() {
        requests.clear();
        queued.clear();
        requestCount.set(0);
        defaultResponse = new StubResponse(200, "{\"ok\":true}", "application/json");
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        String body = readBody(exchange);
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (name != null && values != null && !values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(),
                headers,
                body,
                exchange.getProtocol()));
        StubResponse response = queued.pollFirst();
        if (response == null) {
            response = defaultResponse;
        }
        byte[] bytes = (response.body() == null ? "" : response.body()).getBytes(StandardCharsets.UTF_8);
        if (response.contentType() != null) {
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
        }
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record StubResponse(int status, String body, String contentType) {
    }

    /**
     * One request the stub received. Header names are compared case-insensitively.
     *
     * <p>{@code protocol} is the wire protocol the server saw, e.g. {@code HTTP/1.1}. The
     * companion service (uvicorn/FastAPI) speaks HTTP/1.1 only, so a client that defaults to an
     * h2c upgrade attempt on plaintext connections breaks against it; a test asserts 1.1 here.</p>
     */
    public record RecordedRequest(String method, String path, String query,
                                  Map<String, String> headers, String body, String protocol) {

        public String header(String name) {
            if (name == null || headers == null) {
                return null;
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null
                        && entry.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
