package com.eevee.proxyservice.identity;

import com.eevee.proxyservice.config.ProxyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Optional;

/**
 * Resolves opaque platform user ids to Identity principal email via existing internal HTTP APIs.
 */
@Component
public class IdentityUsageSubjectClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityUsageSubjectClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    @Autowired
    public IdentityUsageSubjectClient(ProxyProperties proxyProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofSeconds(10);
        this.webClient = WebClient.builder()
                .baseUrl(proxyProperties.getKeyService().getBaseUrl())
                .build();
    }

    public static IdentityUsageSubjectClient forTests(
            WebClient webClient,
            ObjectMapper objectMapper,
            Duration requestTimeout
    ) {
        return new IdentityUsageSubjectClient(webClient, objectMapper, requestTimeout);
    }

    private IdentityUsageSubjectClient(WebClient webClient, ObjectMapper objectMapper, Duration requestTimeout) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
    }

    public Optional<String> resolveEmailFromOpaqueOwner(String rawOwnerUserId) {
        if (rawOwnerUserId == null || rawOwnerUserId.isBlank()) {
            return Optional.empty();
        }
        if (UsageSubjectNormalizer.looksLikeEmail(rawOwnerUserId)) {
            return Optional.of(UsageSubjectNormalizer.normalizeEmail(rawOwnerUserId));
        }
        String numericOrRaw = UsageSubjectNormalizer.stripOpaqueUserPrefix(rawOwnerUserId);
        if (numericOrRaw == null || numericOrRaw.isBlank()) {
            return Optional.empty();
        }
        Optional<String> fromPrincipal = fetchPrincipalEmail(numericOrRaw);
        if (fromPrincipal.isPresent()) {
            return fromPrincipal;
        }
        if (UsageSubjectNormalizer.looksLikeEmail(numericOrRaw)) {
            return Optional.of(UsageSubjectNormalizer.normalizeEmail(numericOrRaw));
        }
        return fetchEmailByNumericUserId(numericOrRaw);
    }

    private Optional<String> fetchPrincipalEmail(String q) {
        try {
            String body = webClient.get()
                    .uri(UriComponentsBuilder.fromPath("/internal/users/principal")
                            .queryParam("q", q)
                            .build(true)
                            .toUri())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(requestTimeout);
            return parsePrincipalEmail(body);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.warn("identity principal lookup failed status={} qPrefix={}", e.getStatusCode().value(), qPrefix(q));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("identity principal lookup failed qPrefix={} cause={}", qPrefix(q), e.toString());
            return Optional.empty();
        }
    }

    private Optional<String> fetchEmailByNumericUserId(String userId) {
        try {
            String body = webClient.get()
                    .uri(UriComponentsBuilder.fromPath("/internal/users/email")
                            .queryParam("userId", userId)
                            .build(true)
                            .toUri())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(requestTimeout);
            return parseEmailData(body);
        } catch (WebClientResponseException e) {
            log.warn("identity email lookup failed status={} userIdPrefix={}", e.getStatusCode().value(), qPrefix(userId));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("identity email lookup failed userIdPrefix={} cause={}", qPrefix(userId), e.toString());
            return Optional.empty();
        }
    }

    private Optional<String> parsePrincipalEmail(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }
            JsonNode emailNode = data.path("email");
            if (!emailNode.isTextual()) {
                return Optional.empty();
            }
            String email = UsageSubjectNormalizer.normalizeEmail(emailNode.asText());
            return email != null ? Optional.of(email) : Optional.empty();
        } catch (Exception e) {
            log.warn("failed to parse identity principal response cause={}", e.toString());
            return Optional.empty();
        }
    }

    private Optional<String> parseEmailData(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isTextual()) {
                return Optional.empty();
            }
            String email = UsageSubjectNormalizer.normalizeEmail(data.asText());
            return email != null && !email.isBlank() ? Optional.of(email) : Optional.empty();
        } catch (Exception e) {
            log.warn("failed to parse identity email response cause={}", e.toString());
            return Optional.empty();
        }
    }

    private static String qPrefix(String q) {
        if (q == null) {
            return "-";
        }
        return q.length() <= 8 ? q : q.substring(0, 8);
    }
}
