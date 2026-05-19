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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyClientFingerprintLookupTest {

    private HttpServer identityServer;
    private HttpServer teamServer;

    @AfterEach
    void tearDown() {
        if (identityServer != null) {
            identityServer.stop(0);
        }
        if (teamServer != null) {
            teamServer.stop(0);
        }
    }

    @Test
    void resolvesPersonalKeyViaFingerprintLookupAndHydration() throws Exception {
        String fingerprint = ApiKeyFingerprintNormalizer.sha256HexUtf8("sk-test-personal");
        identityServer = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/internal/v1/api-keys/lookup")) {
                respond(exchange, 200, """
                        {"found":true,"ownerType":"PERSONAL","userId":"u_1","teamId":null,\
                        "keyId":"99","alias":"my-key","status":"ACTIVE","keySource":"managed"}\
                        """);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && path.contains("/internal/api-keys/openai")) {
                respond(exchange, 200, "{\"plainKey\":\"sk-test-personal\",\"keyId\":\"99\"}");
                return;
            }
            respond(exchange, 404, "{}");
        });
        teamServer = startServer(exchange -> respond(exchange, 404, "{\"message\":\"not found\"}"));

        ProxyProperties props = baseProps(identityServer.getAddress().getPort(), teamServer.getAddress().getPort());
        ApiKeyClient client = ApiKeyClient.forTests(props);

        ApiKeyClient.ResolvedApiKey resolved = client.resolveApiKey(
                null,
                null,
                AiProvider.OPENAI,
                null,
                null,
                null,
                fingerprint,
                "corr-1"
        ).block();

        assertThat(resolved).isNotNull();
        assertThat(resolved.plainKey()).isEqualTo("sk-test-personal");
        assertThat(resolved.keyId()).isEqualTo("99");
        assertThat(resolved.ownerUserId()).isEqualTo("u_1");
    }

    @Test
    void teamOwnerReturns502UntilTeamCredentialApiExists() throws Exception {
        String fingerprint = ApiKeyFingerprintNormalizer.sha256HexUtf8("sk-test-team");
        identityServer = startServer(exchange -> respond(exchange, 404, "{}"));
        teamServer = startServer(exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, """
                        {"found":true,"ownerType":"TEAM","userId":null,"teamId":42,\
                        "keyId":"77","alias":"team-key","status":"ACTIVE","keySource":"team"}\
                        """);
                return;
            }
            respond(exchange, 404, "{}");
        });

        ProxyProperties props = baseProps(identityServer.getAddress().getPort(), teamServer.getAddress().getPort());
        ApiKeyClient client = ApiKeyClient.forTests(props);

        assertThatThrownBy(() -> client.resolveApiKey(
                null,
                null,
                AiProvider.OPENAI,
                null,
                null,
                null,
                fingerprint,
                "corr-2"
        ).block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private static HttpServer startServer(IoHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static ProxyProperties baseProps(int identityPort, int teamPort) {
        ProxyProperties props = new ProxyProperties();
        props.getKeyService().setBaseUrl("http://127.0.0.1:" + identityPort);
        props.getKeyService().setInternalToken("test-token");
        props.getKeyService().setCacheTtl("PT1S");
        props.getTeamKeyService().setBaseUrl("http://127.0.0.1:" + teamPort);
        props.getTeamKeyService().setInternalToken("test-token");
        props.getFingerprintLookup().setInternalToken("test-token");
        return props;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @FunctionalInterface
    private interface IoHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
