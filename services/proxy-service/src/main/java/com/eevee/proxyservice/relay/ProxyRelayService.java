package com.eevee.proxyservice.relay;

import com.eevee.proxyservice.key.ApiKeyClient;
import com.eevee.proxyservice.mq.UsageEventPublisher;
import com.eevee.proxyservice.provider.ProviderHandler;
import com.eevee.proxyservice.provider.ProviderRegistry;
import com.eevee.proxyservice.security.UserContext;
import com.eevee.proxyservice.security.UserContextResolver;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProxyRelayService {

    private static final Pattern PROXY_PATH = Pattern.compile("^/proxy/([^/]+)(/.*)?$");
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade"
    );

    private final WebClient upstreamWebClient;
    private final ProviderRegistry providerRegistry;
    private final ApiKeyClient apiKeyClient;
    private final UsageEventPublisher usageEventPublisher;
    private final UserContextResolver userContextResolver;
    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    public ProxyRelayService(
            WebClient upstreamWebClient,
            ProviderRegistry providerRegistry,
            ApiKeyClient apiKeyClient,
            UsageEventPublisher usageEventPublisher,
            UserContextResolver userContextResolver
    ) {
        this.upstreamWebClient = upstreamWebClient;
        this.providerRegistry = providerRegistry;
        this.apiKeyClient = apiKeyClient;
        this.usageEventPublisher = usageEventPublisher;
        this.userContextResolver = userContextResolver;
    }

    public Mono<ResponseEntity<Flux<DataBuffer>>> relay(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        Matcher m = PROXY_PATH.matcher(path);
        if (!m.matches()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        String providerSegment = m.group(1);
        String remainderRaw = m.group(2);
        final String remainder = (remainderRaw == null || remainderRaw.isBlank()) ? "/" : remainderRaw;
        AiProvider provider = AiProvider.fromPathSegment(providerSegment);
        ProviderHandler handler = providerRegistry.get(provider);

        return userContextResolver.fromExchange(exchange)
                .flatMap(ctx -> apiKeyClient.resolveApiKey(ctx.userId(), provider)
                        .flatMap(resolvedApiKey -> forward(exchange, ctx, handler, provider, remainder, resolvedApiKey)));
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> forward(
            ServerWebExchange exchange,
            UserContext ctx,
            ProviderHandler handler,
            AiProvider provider,
            String remainderPath,
            ApiKeyClient.ResolvedApiKey resolvedApiKey
    ) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build());
        }

        String rawQuery = exchange.getRequest().getURI().getRawQuery();
        URI upstreamUri = handler.buildUpstreamUri(handler.baseUrl(), remainderPath, rawQuery, resolvedApiKey.plainKey());

        HttpHeaders outgoing = copyAndSanitizeHeaders(exchange.getRequest().getHeaders(), handler);
        handler.applyUpstreamAuth(outgoing, resolvedApiKey.plainKey());

        boolean streaming = isStreamingRequest(exchange);

        WebClient.RequestBodySpec spec = upstreamWebClient.method(method)
                .uri(upstreamUri)
                .headers(h -> h.addAll(outgoing));

        WebClient.RequestHeadersSpec<?> withBody;
        if (requiresBody(method)) {
            withBody = spec.body(BodyInserters.fromDataBuffers(exchange.getRequest().getBody()));
        } else {
            withBody = spec;
        }

        return withBody.exchangeToMono(response -> mapResponse(
                response,
                handler,
                ctx,
                provider,
                resolvedApiKey,
                exchange.getRequest().getPath().value(),
                streaming
        ));
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> mapResponse(
            ClientResponse response,
            ProviderHandler handler,
            UserContext ctx,
            AiProvider provider,
            ApiKeyClient.ResolvedApiKey resolvedApiKey,
            String requestPath,
            boolean streaming
    ) {
        HttpHeaders responseHeaders = filterResponseHeaders(response.headers().asHttpHeaders());
        HttpStatusCode status = response.statusCode();

        MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
        if (streaming || MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
            StringBuilder acc = new StringBuilder();
            String upstreamHost = upstreamHost(response);
            Flux<DataBuffer> body = response.bodyToFlux(DataBuffer.class)
                    .map(db -> {
                        int n = db.readableByteCount();
                        byte[] copy = new byte[n];
                        db.read(copy);
                        acc.append(new String(copy, StandardCharsets.UTF_8));
                        return bufferFactory.wrap(copy);
                    })
                    .doOnComplete(() -> {
                        TokenUsage u = handler.parseUsageFromSse(acc.toString());
                        publishUsage(ctx, provider, resolvedApiKey, requestPath, u, upstreamHost, true, status).subscribe();
                    });
            return Mono.just(ResponseEntity.status(status).headers(responseHeaders).body(body));
        }

        URI requestUri = response.request().getURI();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(bodyStr -> {
                    TokenUsage u = handler.parseUsageFromResponseJson(bodyStr);
                    byte[] bytes = bodyStr.getBytes(StandardCharsets.UTF_8);
                    Flux<DataBuffer> flux = Flux.just(bufferFactory.wrap(bytes));
                    return publishUsage(ctx, provider, resolvedApiKey, requestPath, u, safeHost(requestUri), false, status)
                            .thenReturn(ResponseEntity.status(status).headers(responseHeaders).body(flux));
                });
    }

    private static String safeHost(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        return uri.getHost();
    }

    private static String upstreamHost(ClientResponse response) {
        try {
            return safeHost(response.request().getURI());
        } catch (Exception ignored) {
            return "";
        }
    }

    private Mono<Void> publishUsage(
            UserContext ctx,
            AiProvider provider,
            ApiKeyClient.ResolvedApiKey resolvedApiKey,
            String requestPath,
            TokenUsage usage,
            String upstreamHost,
            boolean streaming,
            HttpStatusCode upstreamStatus
    ) {
        boolean successful = upstreamStatus.is2xxSuccessful();
        Integer statusCode = upstreamStatus.value();
        UsageRecordedEvent event = new UsageRecordedEvent(
                null,
                null,
                ctx.correlationId(),
                ctx.userId(),
                ctx.organizationId(),
                ctx.teamId(),
                resolvedApiKey.keyId(),
                resolvedApiKey.keyFingerprint(),
                resolvedApiKey.keySource(),
                provider,
                usage != null ? usage.model() : null,
                usage,
                BigDecimal.ZERO,
                requestPath,
                upstreamHost,
                streaming,
                successful,
                statusCode
        );
        return usageEventPublisher.publish(event);
    }

    private static boolean requiresBody(HttpMethod method) {
        return switch (method.name()) {
            case "POST", "PUT", "PATCH" -> true;
            default -> false;
        };
    }

    private static boolean isStreamingRequest(ServerWebExchange exchange) {
        String accept = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT);
        return accept != null && accept.toLowerCase(Locale.ROOT).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private static HttpHeaders copyAndSanitizeHeaders(HttpHeaders incoming, ProviderHandler handler) {
        HttpHeaders out = new HttpHeaders();
        incoming.forEach((name, values) -> {
            if (shouldDrop(name, handler)) {
                return;
            }
            out.addAll(name, values);
        });
        HttpHeaders blocked = handler.blockedIncomingHeaders();
        blocked.forEach((name, values) -> out.remove(name));
        out.remove(HttpHeaders.HOST);
        return out;
    }

    private static boolean shouldDrop(String name, ProviderHandler handler) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (HOP_BY_HOP.contains(lower)) {
            return true;
        }
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
            return true;
        }
        return lower.equals("x-api-key") || lower.equals("x-goog-api-key");
    }

    private static HttpHeaders filterResponseHeaders(HttpHeaders in) {
        HttpHeaders out = new HttpHeaders();
        in.forEach((name, values) -> {
            if (HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            out.addAll(name, values);
        });
        return out;
    }
}
