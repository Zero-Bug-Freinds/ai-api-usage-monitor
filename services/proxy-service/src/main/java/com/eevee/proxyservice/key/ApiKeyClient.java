package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Resolves provider API keys from API Key Service (or mock). Keys are never logged.
 */
@Service
public class ApiKeyClient {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyClient.class);

    private final ProxyProperties proxyProperties;
    private final WebClient identityKeyServiceWebClient;
    private final WebClient teamKeyServiceWebClient;
    private final LoadingCache<String, ResolvedApiKey> cache;
    private final Map<String, List<ReverseLookupEntry>> reverseLookupIndexByHash = new ConcurrentHashMap<>();

    public ApiKeyClient(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
        this.identityKeyServiceWebClient = WebClient.builder()
                .baseUrl(proxyProperties.getKeyService().getBaseUrl())
                .build();
        this.teamKeyServiceWebClient = WebClient.builder()
                .baseUrl(proxyProperties.getTeamKeyService().getBaseUrl())
                .build();
        Duration ttl = Duration.parse(proxyProperties.getKeyService().getCacheTtl());
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .build(this::loadKeyBlocking);
        buildReverseLookupIndex();
    }

    /**
     * @param keyLookupUserId numeric platform user id when available, else gateway subject (e.g. email)
     */
    public Mono<ResolvedApiKey> resolveApiKey(
            String keyLookupUserId,
            String teamId,
            AiProvider provider,
            String requestedApiKeyId,
            String requestedAlias,
            String rawApiKey
    ) {
        String normalizedTeamId = teamId == null ? "" : teamId.trim();
        String normalizedRequestedApiKeyId = normalizeSelector(requestedApiKeyId);
        String normalizedRequestedAlias = normalizeSelector(requestedAlias);
        String normalizedRawApiKey = normalizeSelector(rawApiKey);
        if (normalizedRequestedApiKeyId != null || normalizedRequestedAlias != null) {
            return Mono.fromCallable(() -> loadKeyBlocking(
                            requireLookupUserId(keyLookupUserId),
                            normalizedTeamId,
                            provider,
                            normalizedRequestedApiKeyId,
                            normalizedRequestedAlias,
                            null
                    ))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        if (normalizedRawApiKey != null) {
            return Mono.fromCallable(() -> loadByReverseLookup(normalizedRawApiKey, provider))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        String requiredLookupUserId = requireLookupUserId(keyLookupUserId);
        return Mono.fromCallable(() -> cache.get(requiredLookupUserId + ":" + normalizedTeamId + ":" + provider.pathSegment()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ResolvedApiKey loadKeyBlocking(String cacheKey) {
        int firstIdx = cacheKey.indexOf(':');
        int lastIdx = cacheKey.lastIndexOf(':');
        if (firstIdx <= 0 || lastIdx <= firstIdx) {
            throw new IllegalStateException("invalid cache key");
        }
        String keyLookupUserId = cacheKey.substring(0, firstIdx);
        String teamId = cacheKey.substring(firstIdx + 1, lastIdx);
        String segment = cacheKey.substring(lastIdx + 1);
        AiProvider provider = AiProvider.fromPathSegment(segment);
        return loadKeyBlocking(keyLookupUserId, teamId, provider, null, null, null);
    }

    private ResolvedApiKey loadKeyBlocking(
            String keyLookupUserId,
            String teamId,
            AiProvider provider,
            String requestedApiKeyId,
            String requestedAlias,
            String rawApiKey
    ) {
        boolean teamRequest = !teamId.isBlank();
        String userForLog = masked(keyLookupUserId);
        String teamForLog = teamRequest ? masked(teamId) : "-";
        String lookupTarget = teamRequest ? "team-service" : "identity-service";

        String mock = resolveMockKey(provider);
        if (mock != null && !mock.isBlank()) {
            log.info("Resolved API key from mock lookupTarget={} provider={} teamId={} user={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog);
            return new ResolvedApiKey(mock, null, null, requestedAlias, fingerprint(mock), "mock");
        }

        try {
            KeyResponse body;
            if (teamRequest) {
                body = loadTeamKey(keyLookupUserId, teamId, provider, requestedApiKeyId, requestedAlias);
            } else {
                body = loadIdentityKey(keyLookupUserId, provider, requestedApiKeyId, requestedAlias);
            }
            if (body == null || body.plainKey() == null || body.plainKey().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Key lookup returned empty key");
            }
            if (!isActiveStatus(body.status())) {
                throw new ResponseStatusException(NOT_FOUND, "존재하지 않은 API key 입니다");
            }
            String resolvedAlias = hasText(body.alias()) ? body.alias() : requestedAlias;
            log.info("Resolved API key lookupTarget={} provider={} teamId={} user={} keySource={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog, teamRequest ? "team" : "managed");
            return new ResolvedApiKey(
                    body.plainKey(),
                    body.keyId(),
                    teamRequest ? body.keyId() : null,
                    resolvedAlias,
                    fingerprint(body.plainKey()),
                    teamRequest ? "team" : "managed"
            );
        } catch (WebClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 409) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "동일한 별칭을 가진 키가 여러 개 존재합니다. 정확한 별칭을 입력해주세요.",
                        e
                );
            }
            if (statusCode == 404) {
                log.warn("API key lookup not found lookupTarget={} provider={} teamId={} user={} status={}",
                        lookupTarget, provider.pathSegment(), teamForLog, userForLog, e.getStatusCode().value());
                throw new ResponseStatusException(NOT_FOUND, "존재하지 않은 API key 입니다", e);
            }
            log.warn("API key lookup failed lookupTarget={} provider={} teamId={} user={} status={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog, e.getStatusCode().value());
            throw new ResponseStatusException(BAD_GATEWAY, "Key lookup failed: " + e.getStatusCode(), e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("API key lookup connection failed lookupTarget={} provider={} teamId={} user={} cause={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog, e.toString());
            throw new ResponseStatusException(BAD_GATEWAY, "Key lookup connection failed", e);
        }
    }

    private KeyResponse loadIdentityKey(
            String keyLookupUserId,
            AiProvider provider,
            String requestedApiKeyId,
            String requestedAlias
    ) {
        String token = proxyProperties.getKeyService().getInternalToken();
        String primaryProvider = provider.pathSegment();
        try {
            return loadIdentityKeyByProviderSegment(
                    keyLookupUserId,
                    primaryProvider,
                    token,
                    requestedApiKeyId,
                    requestedAlias
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() != 404 || provider != AiProvider.GOOGLE) {
                throw e;
            }
        }

        try {
            return loadIdentityKeyByProviderSegment(
                    keyLookupUserId,
                    "gemini",
                    token,
                    requestedApiKeyId,
                    requestedAlias
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        NOT_FOUND,
                        "존재하지 않은 API key 입니다",
                        e
                );
            }
            throw e;
        }

    }

    private KeyResponse loadIdentityKeyByProviderSegment(
            String keyLookupUserId,
            String providerSegment,
            String token,
            String requestedApiKeyId,
            String requestedAlias
    ) {
        return identityKeyServiceWebClient.get()
                .uri(uriBuilder -> buildKeyLookupUri(
                        uriBuilder.path("/internal/api-keys/{provider}"),
                        providerSegment,
                        keyLookupUserId,
                        null,
                        requestedApiKeyId,
                        requestedAlias
                ))
                .headers(h -> {
                    if (token != null && !token.isBlank()) {
                        h.setBearerAuth(token);
                    }
                })
                .retrieve()
                .bodyToMono(KeyResponse.class)
                .block(Duration.ofSeconds(10));
    }

    private KeyResponse loadTeamKey(
            String keyLookupUserId,
            String teamId,
            AiProvider provider,
            String requestedApiKeyId,
            String requestedAlias
    ) {
        ProxyProperties.TeamKeyService teamKeyService = proxyProperties.getTeamKeyService();
        String token = teamKeyService.getInternalToken();
        String pathTemplate = teamKeyService.getPathTemplate();
        String primaryProvider = provider.pathSegment();
        try {
            return loadTeamKeyByProviderSegment(
                    keyLookupUserId,
                    teamId,
                    primaryProvider,
                    token,
                    pathTemplate,
                    requestedApiKeyId,
                    requestedAlias
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() != 404 || provider != AiProvider.GOOGLE) {
                throw e;
            }
        }

        try {
            return loadTeamKeyByProviderSegment(
                    keyLookupUserId,
                    teamId,
                    "gemini",
                    token,
                    pathTemplate,
                    requestedApiKeyId,
                    requestedAlias
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        NOT_FOUND,
                        "존재하지 않은 API key 입니다",
                        e
                );
            }
            throw e;
        }
    }

    private KeyResponse loadTeamKeyByProviderSegment(
            String keyLookupUserId,
            String teamId,
            String providerSegment,
            String token,
            String pathTemplate,
            String requestedApiKeyId,
            String requestedAlias
    ) {
        return teamKeyServiceWebClient.get()
                .uri(uriBuilder -> buildKeyLookupUri(
                        uriBuilder.path(pathTemplate),
                        providerSegment,
                        keyLookupUserId,
                        teamId,
                        requestedApiKeyId,
                        requestedAlias
                ))
                .headers(h -> {
                    if (token != null && !token.isBlank()) {
                        h.setBearerAuth(token);
                    }
                })
                .retrieve()
                .bodyToMono(KeyResponse.class)
                .block(Duration.ofSeconds(10));
    }

    private static java.net.URI buildKeyLookupUri(
            UriBuilder uriBuilder,
            String providerSegment,
            String keyLookupUserId,
            String teamId,
            String requestedApiKeyId,
            String requestedAlias
    ) {
        UriBuilder builder = uriBuilder.queryParam("userId", keyLookupUserId);
        if (hasText(teamId)) {
            builder = builder.queryParam("teamId", teamId);
        }
        if (hasText(requestedApiKeyId)) {
            builder = builder.queryParam("apiKeyId", requestedApiKeyId);
        } else if (hasText(requestedAlias)) {
            builder = builder.queryParam("alias", requestedAlias);
        }
        return builder.build(providerSegment);
    }

    /**
     * Uses provider-specific mock keys when set; otherwise falls back to legacy {@code mock-key}.
     */
    private String resolveMockKey(AiProvider provider) {
        ProxyProperties.KeyService ks = proxyProperties.getKeyService();
        String specific = switch (provider) {
            case OPENAI -> ks.getMockKeyOpenai();
            case GOOGLE -> ks.getMockKeyGoogle();
            case ANTHROPIC -> null;
        };
        if (specific != null && !specific.isBlank()) {
            return specific;
        }
        String legacy = ks.getMockKey();
        return (legacy != null && !legacy.isBlank()) ? legacy : null;
    }

    static List<String> providerSegmentsForLookup(AiProvider provider) {
        return List.of(provider.pathSegment());
    }

    private static String masked(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        int keep = Math.min(4, value.length());
        return value.substring(0, keep) + "***";
    }

    private static String normalizeSelector(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String fingerprint(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record ResolvedApiKey(
            String plainKey,
            String keyId,
            String teamApiKeyId,
            String alias,
            String keyFingerprint,
            String keySource
    ) {
    }

    private ResolvedApiKey loadByReverseLookup(String rawApiKey, AiProvider provider) {
        String rawHash = sha256Hex(rawApiKey);
        List<ReverseLookupEntry> entries = reverseLookupIndexByHash.get(rawHash);
        if (entries == null || entries.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "존재하지 않은 API key 입니다");
        }
        List<ReverseLookupEntry> matches = entries.stream()
                .filter(entry -> entry.provider() == null || entry.provider() == provider)
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "존재하지 않은 API key 입니다");
        }
        List<ReverseLookupEntry> active = matches.stream()
                .filter(entry -> isActiveStatus(entry.status()))
                .toList();
        if (active.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "존재하지 않은 API key 입니다");
        }
        if (active.size() > 1) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "동일한 별칭을 가진 키가 여러 개 존재합니다. 정확한 별칭을 입력해주세요."
            );
        }
        ReverseLookupEntry selected = active.get(0);
        log.info("Resolved API key via reverse lookup provider={} keyId={} source={}",
                provider.pathSegment(), masked(selected.keyId()), selected.keySource());
        String teamApiKeyId = hasText(selected.teamId()) ? selected.keyId() : null;
        return new ResolvedApiKey(
                rawApiKey,
                selected.keyId(),
                teamApiKeyId,
                selected.alias(),
                fingerprint(rawApiKey),
                selected.keySource()
        );
    }

    private void buildReverseLookupIndex() {
        List<ProxyProperties.ReverseLookupMock> configured = proxyProperties.getKeyService().getReverseLookupMocks();
        if (configured == null || configured.isEmpty()) {
            return;
        }
        Map<String, List<ReverseLookupEntry>> grouped = new ConcurrentHashMap<>();
        for (ProxyProperties.ReverseLookupMock item : configured) {
            String keyHash = resolveConfiguredHash(item);
            if (!hasText(keyHash) || !hasText(item.getKeyId())) {
                continue;
            }
            AiProvider provider = parseProviderOrNull(item.getProvider());
            ReverseLookupEntry entry = new ReverseLookupEntry(
                    keyHash,
                    provider,
                    item.getKeyId().trim(),
                    normalizeSelector(item.getUserId()),
                    normalizeSelector(item.getTeamId()),
                    normalizeSelector(item.getAlias()),
                    normalizeSelector(item.getStatus()),
                    hasText(item.getKeySource()) ? item.getKeySource().trim() : "reverse_lookup"
            );
            grouped.computeIfAbsent(keyHash, __ -> new ArrayList<>()).add(entry);
        }
        enforceNoCrossScopeDuplicate(grouped);
        grouped.forEach((k, v) -> reverseLookupIndexByHash.put(k, v.stream()
                .sorted(Comparator.comparing(ReverseLookupEntry::keyId))
                .toList()));
    }

    private static void enforceNoCrossScopeDuplicate(Map<String, List<ReverseLookupEntry>> grouped) {
        for (Map.Entry<String, List<ReverseLookupEntry>> item : grouped.entrySet()) {
            List<ReverseLookupEntry> entries = item.getValue();
            boolean hasTeam = entries.stream().anyMatch(e -> hasText(e.teamId()));
            boolean hasPersonal = entries.stream().anyMatch(e -> !hasText(e.teamId()));
            if (hasTeam && hasPersonal) {
                throw new IllegalStateException("personal/team duplicate raw key registration is not allowed");
            }
        }
    }

    private static String resolveConfiguredHash(ProxyProperties.ReverseLookupMock item) {
        if (hasText(item.getRawKeySha256())) {
            return item.getRawKeySha256().trim().toLowerCase(Locale.ROOT);
        }
        if (hasText(item.getRawKey())) {
            return sha256Hex(item.getRawKey().trim());
        }
        return null;
    }

    private static AiProvider parseProviderOrNull(String provider) {
        if (!hasText(provider)) {
            return null;
        }
        return AiProvider.fromPathSegment(provider.trim().toLowerCase(Locale.ROOT));
    }

    private static String requireLookupUserId(String keyLookupUserId) {
        String normalized = normalizeSelector(keyLookupUserId);
        if (!hasText(normalized)) {
            throw new ResponseStatusException(NOT_FOUND, "존재하지 않은 API key 입니다");
        }
        return normalized;
    }

    private static boolean isActiveStatus(String status) {
        if (!hasText(status)) {
            return true;
        }
        return Objects.equals(status.trim().toUpperCase(Locale.ROOT), "ACTIVE");
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record KeyResponse(String plainKey, String keyId, String alias, String status) {
    }

    private record ReverseLookupEntry(
            String keyHash,
            AiProvider provider,
            String keyId,
            String userId,
            String teamId,
            String alias,
            String status,
            String keySource
    ) {
    }
}
