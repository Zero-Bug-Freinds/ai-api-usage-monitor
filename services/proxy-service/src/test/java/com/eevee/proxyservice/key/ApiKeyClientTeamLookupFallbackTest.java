package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyClientTeamLookupFallbackTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void teamLookupFallsBackToGeminiWhenGoogleNotFound() throws Exception {
        AtomicInteger googleCalls = new AtomicInteger();
        AtomicInteger geminiCalls = new AtomicInteger();
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/google")) {
                googleCalls.incrementAndGet();
                respond(exchange, 404, "{\"message\":\"not found\"}");
                return;
            }
            if (path.endsWith("/gemini")) {
                geminiCalls.incrementAndGet();
                respond(exchange, 200, "{\"plainKey\":\"AIza-legacy\",\"keyId\":\"team-key-1\"}");
                return;
            }
            respond(exchange, 404, "{\"message\":\"unknown\"}");
        });

        ApiKeyClient client = new ApiKeyClient(baseProps(server.getAddress().getPort()));
        ApiKeyClient.ResolvedApiKey resolved = client.resolveApiKey("user-1", "123", AiProvider.GOOGLE, null, null, null).block();

        assertThat(resolved).isNotNull();
        assertThat(resolved.plainKey()).isEqualTo("AIza-legacy");
        assertThat(resolved.keyId()).isEqualTo("team-key-1");
        assertThat(googleCalls.get()).isEqualTo(1);
        assertThat(geminiCalls.get()).isEqualTo(1);
    }

    @Test
    void teamLookupReturnsNotFoundWithProviderAndTeamContextWhenAllMissed() throws Exception {
        startServer(exchange -> respond(exchange, 404, "{\"message\":\"not found\"}"));

        ApiKeyClient client = new ApiKeyClient(baseProps(server.getAddress().getPort()));

        assertThatThrownBy(() -> client.resolveApiKey("user-1", "999", AiProvider.GOOGLE, null, null, null).block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusException = (ResponseStatusException) ex;
                    assertThat(statusException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(statusException.getReason()).isEqualTo("존재하지 않은 API key 입니다");
                });
    }

    @Test
    void teamLookupDoesNotFallbackOnNon404Error() throws Exception {
        AtomicInteger googleCalls = new AtomicInteger();
        AtomicInteger geminiCalls = new AtomicInteger();
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/google")) {
                googleCalls.incrementAndGet();
                respond(exchange, 500, "{\"message\":\"upstream error\"}");
                return;
            }
            if (path.endsWith("/gemini")) {
                geminiCalls.incrementAndGet();
                respond(exchange, 200, "{\"plainKey\":\"AIza-legacy\",\"keyId\":\"team-key-1\"}");
                return;
            }
            respond(exchange, 404, "{\"message\":\"unknown\"}");
        });

        ApiKeyClient client = new ApiKeyClient(baseProps(server.getAddress().getPort()));

        assertThatThrownBy(() -> client.resolveApiKey("user-1", "123", AiProvider.GOOGLE, null, null, null).block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusException = (ResponseStatusException) ex;
                    assertThat(statusException.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
        assertThat(googleCalls.get()).isEqualTo(1);
        assertThat(geminiCalls.get()).isEqualTo(0);
    }

    private void startServer(IoHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/api-keys", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private static ProxyProperties baseProps(int port) {
        ProxyProperties props = new ProxyProperties();
        props.getKeyService().setBaseUrl("http://127.0.0.1:" + port);
        props.getKeyService().setCacheTtl("PT1S");
        props.getTeamKeyService().setBaseUrl("http://127.0.0.1:" + port);
        props.getTeamKeyService().setPathTemplate("/internal/api-keys/{provider}");
        return props;
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    @FunctionalInterface
    private interface IoHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
