package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyClientManagedKeyUsageSubjectTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void managedJwtPath_setsUsageSubjectFromGatewayEmail_notLookupPk() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/google")) {
                assertThat(query).contains("userId=3");
                assertThat(query).contains("apiKeyId=42");
                respond(exchange, 200, "{\"plainKey\":\"AIza-test\",\"keyId\":\"42\",\"status\":\"ACTIVE\"}");
                return;
            }
            respond(exchange, 404, "{}");
        });

        ProxyProperties props = baseProps(server.getAddress().getPort());
        props.getUsageSubject().setEnabled(true);
        ApiKeyClient client = ApiKeyClient.forTests(props);

        ApiKeyClient.ResolvedApiKey resolved = client.resolveApiKey(
                "3",
                "",
                AiProvider.GOOGLE,
                "42",
                null,
                null,
                null,
                null,
                "User@Example.com"
        ).block();

        assertThat(resolved).isNotNull();
        assertThat(resolved.plainKey()).isEqualTo("AIza-test");
        assertThat(resolved.usageSubjectUserId()).isEqualTo("user@example.com");
        assertThat(resolved.keySource()).isEqualTo("managed");
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
        props.getUsageSubject().setCacheTtl("PT1S");
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
