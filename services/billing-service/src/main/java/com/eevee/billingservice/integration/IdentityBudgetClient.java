package com.eevee.billingservice.integration;

import com.eevee.billingservice.config.IdentityProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
            BudgetEnvelope env = objectMapper.readValue(body, BudgetEnvelope.class);
            return Optional.ofNullable(env.monthlyBudgetUsd());
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BudgetEnvelope(BigDecimal monthlyBudgetUsd) {
    }
}
