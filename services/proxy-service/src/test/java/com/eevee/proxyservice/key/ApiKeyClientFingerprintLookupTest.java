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

import com.eevee.proxyservice.identity.UsageSubjectResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/internal/users/principal")) {
                respond(exchange, 200, """
                        {"success":true,"message":"ok","data":{"userId":"1","email":"user@test.com"}}\
                        """);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/internal/users/email")) {
                respond(exchange, 200, """
                        {"success":true,"message":"ok","data":"user@test.com"}\
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
        UsageSubjectResolver usageSubjectResolver = mock(UsageSubjectResolver.class);
        when(usageSubjectResolver.resolveForFingerprintOwner(any(), any())).thenReturn("user@test.com");
        ApiKeyClient client = ApiKeyClient.forTests(props, usageSubjectResolver);

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
        assertThat(resolved.ownerUserId()).isEqualTo("user@test.com");
    }

    @Test
    void teamOwnerResolvesViaFingerprintLookupAndCredentialApi() throws Exception {
        String fingerprint = ApiKeyFingerprintNormalizer.sha256HexUtf8("sk-test-team");
        identityServer = startServer(exchange -> respond(exchange, 404, "{}"));
        teamServer = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/internal/v1/api-keys/lookup")) {
                respond(exchange, 200, """
                        {"found":true,"ownerType":"TEAM","userId":null,"teamId":42,\
                        "keyId":"77","alias":"team-key","status":"ACTIVE","keySource":"team"}\
                        """);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())
                    && path.contains("/internal/v1/team-api-keys/77/credential")) {
                respond(exchange, 200, "{\"plainKey\":\"sk-test-team\",\"keyId\":\"77\"}");
                return;
            }
            respond(exchange, 404, "{}");
        });

        ProxyProperties props = baseProps(identityServer.getAddress().getPort(), teamServer.getAddress().getPort());
        ApiKeyClient client = ApiKeyClient.forTests(props);

        ApiKeyClient.ResolvedApiKey resolved = client.resolveApiKey(
                "member@team.com",
                "42",
                AiProvider.OPENAI,
                null,
                null,
                null,
                fingerprint,
                "corr-2"
        ).block();

        assertThat(resolved).isNotNull();
        assertThat(resolved.plainKey()).isEqualTo("sk-test-team");
        assertThat(resolved.keyId()).isEqualTo("77");
        assertThat(resolved.ownerTeamId()).isEqualTo("42");
        assertThat(resolved.ownerUserId()).isEqualTo("member@team.com");
        assertThat(resolved.keySource()).isEqualTo("team");
    }

    @Test
    void teamOwnerCredentialNotFound_returns404() throws Exception {
        String fingerprint = ApiKeyFingerprintNormalizer.sha256HexUtf8("sk-missing-team");
        identityServer = startServer(exchange -> respond(exchange, 404, "{}"));
        teamServer = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/internal/v1/api-keys/lookup")) {
                respond(exchange, 200, """
                        {"found":true,"ownerType":"TEAM","userId":null,"teamId":42,\
                        "keyId":"77","alias":"team-key","status":"ACTIVE","keySource":"team"}\
                        """);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())
                    && path.contains("/internal/v1/team-api-keys/77/credential")) {
                respond(exchange, 404, "{\"message\":\"not found\"}");
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
                "corr-3"
        ).block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
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
