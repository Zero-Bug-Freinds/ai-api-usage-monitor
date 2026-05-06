package com.eevee.billingservice.integration;

import com.eevee.billingservice.config.IdentityProperties;
import com.eevee.usage.events.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Optional HTTP client for monthly budget; disabled or 404 yields empty.
 */
@Component
public class IdentityBudgetClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityBudgetClient.class);

    private final IdentityProperties identityProperties;
    private final ObjectMapper objectMapper;

    public IdentityBudgetClient(IdentityProperties identityProperties, ObjectMapper objectMapper) {
        this.identityProperties = identityProperties;
        this.objectMapper = objectMapper;
    }

    public Optional<BigDecimal> fetchMonthlyBudgetUsd(String userId) {
        return fetchMonthlyBudgetEnvelope(userId).map(IdentityMonthlyBudgetEnvelope::monthlyBudgetUsd);
    }

    public Optional<BigDecimal> fetchMonthlyBudgetUsdForKey(String userId, AiProvider provider, String apiKeyId) {
        return fetchMonthlyBudgetKeyRow(userId, provider, apiKeyId).map(IdentityBudgetKeyRow::monthlyBudgetUsd);
    }

    public Optional<IdentityBudgetKeyRow> fetchMonthlyBudgetKeyRow(String userId, AiProvider provider, String apiKeyId) {
        if (provider == null || apiKeyId == null || apiKeyId.isBlank()) {
            return Optional.empty();
        }
        Optional<Long> externalKeyId = tryParseLong(apiKeyId);
        if (externalKeyId.isEmpty()) {
            return Optional.empty();
        }

        String identityProvider = toIdentityProviderName(provider);
        return fetchMonthlyBudgetEnvelope(userId)
                .flatMap(env -> env.monthlyBudgetsByKey().stream()
                        .filter(v -> v != null && v.externalApiKeyId() != null)
                        .filter(v -> v.externalApiKeyId().equals(externalKeyId.get()))
                        .filter(v -> v.provider() != null && v.provider().equalsIgnoreCase(identityProvider))
                        .findFirst()
                );
    }

    /**
     * Returns the Identity budget envelope as parsed JSON, when configured and available.
     */
    public Optional<IdentityMonthlyBudgetEnvelope> fetchMonthlyBudgetEnvelope(String userId) {
        return fetchBudgetEnvelopeInternal(userId);
    }

    private Optional<IdentityMonthlyBudgetEnvelope> fetchBudgetEnvelopeInternal(String userId) {
        if (!identityProperties.isEnabled()) {
            return Optional.empty();
        }
        String base = identityProperties.getBaseUrl();
        String template = identityProperties.getBudgetPathTemplate();
        if (base == null || base.isBlank() || template == null || template.isBlank()) {
            return Optional.empty();
        }
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedTemplate = template.startsWith("/") ? template : "/" + template;
        String uriTemplate = normalizedBase + normalizedTemplate;
        URI uri = template.contains("{userId}")
                ? UriComponentsBuilder.fromUriString(uriTemplate)
                .buildAndExpand(userId)
                .encode(StandardCharsets.UTF_8)
                .toUri()
                : UriComponentsBuilder.fromUriString(uriTemplate)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        try {
            String body = RestClient.create()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            IdentityMonthlyBudgetEnvelope env = objectMapper.readValue(body, IdentityMonthlyBudgetEnvelope.class);
            return Optional.of(env);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.debug("Identity budget lookup failed status={} uri={}", e.getStatusCode().value(), uri);
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Identity budget lookup failed uri={}", uri, e);
            return Optional.empty();
        }
    }

    private static Optional<Long> tryParseLong(String raw) {
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String toIdentityProviderName(AiProvider provider) {
        return switch (provider) {
            case OPENAI -> "OPENAI";
            case ANTHROPIC -> "ANTHROPIC";
            case GOOGLE -> "GEMINI";
        };
    }
}
