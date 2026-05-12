package com.eevee.billingservice.consumer;

import com.eevee.billingservice.domain.BillingTeamApiKeyEntity;
import com.eevee.billingservice.domain.BillingTeamApiKeyEventProcessedEntity;
import com.eevee.billingservice.repository.BillingTeamApiKeyEventProcessedRepository;
import com.eevee.billingservice.repository.BillingTeamApiKeyRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class TeamApiKeyStatusChangedEventListener {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyStatusChangedEventListener.class);

    private final ObjectMapper objectMapper;
    private final BillingTeamApiKeyRepository teamApiKeyRepository;
    private final BillingTeamApiKeyEventProcessedRepository processedRepository;

    public TeamApiKeyStatusChangedEventListener(
            ObjectMapper objectMapper,
            BillingTeamApiKeyRepository teamApiKeyRepository,
            BillingTeamApiKeyEventProcessedRepository processedRepository
    ) {
        this.objectMapper = objectMapper;
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.processedRepository = processedRepository;
    }

    @RabbitListener(queues = "${billing.rabbit.team-api-key-in.queue}")
    @Transactional
    public void onMessage(String json) {
        final TeamApiKeyStatusChangedPayload payload;
        try {
            payload = objectMapper.readValue(json, TeamApiKeyStatusChangedPayload.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize TeamApiKeyStatusChanged payload", ex);
            throw new IllegalStateException("billing team api key event handling failed", ex);
        }

        UUID eventId = parseUuid(payload.eventId());
        if (eventId == null) {
            log.warn("Skipping TeamApiKeyStatusChangedEvent without valid eventId teamApiKeyId={}", payload.teamApiKeyId());
            return;
        }
        if (processedRepository.existsById(eventId)) {
            return;
        }

        Long teamApiKeyId = payload.teamApiKeyId();
        Long teamId = payload.teamId();
        if (teamApiKeyId == null || teamId == null) {
            log.warn("Skipping TeamApiKeyStatusChangedEvent missing ids teamApiKeyId={} teamId={}", teamApiKeyId, teamId);
            processedRepository.save(new BillingTeamApiKeyEventProcessedEntity(eventId, Instant.now()));
            return;
        }

        String alias = payload.alias() == null ? "" : payload.alias().trim();
        String provider = payload.provider() == null ? "" : payload.provider().trim();
        BigDecimal monthlyBudgetUsd = payload.monthlyBudgetUsd() != null ? payload.monthlyBudgetUsd() : BigDecimal.ZERO;
        String status = payload.status() == null ? "UNKNOWN" : payload.status().trim();
        Instant occurredAt = payload.occurredAt() != null ? payload.occurredAt() : Instant.now();
        Instant now = Instant.now();

        BillingTeamApiKeyEntity entity = teamApiKeyRepository.findById(teamApiKeyId).orElse(null);
        if (entity == null) {
            entity = new BillingTeamApiKeyEntity(
                    teamApiKeyId,
                    teamId,
                    alias,
                    provider,
                    monthlyBudgetUsd,
                    status,
                    payload.retainLogs(),
                    occurredAt,
                    now
            );
        } else {
            entity.applyStatusChanged(
                    teamId,
                    alias,
                    provider,
                    monthlyBudgetUsd,
                    status,
                    payload.retainLogs(),
                    occurredAt,
                    now
            );
        }
        teamApiKeyRepository.save(entity);
        processedRepository.save(new BillingTeamApiKeyEventProcessedEntity(eventId, now));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamApiKeyStatusChangedPayload(
            String schemaVersion,
            String eventId,
            String eventType,
            Instant occurredAt,
            Long teamId,
            Long teamApiKeyId,
            String alias,
            String provider,
            BigDecimal monthlyBudgetUsd,
            String status,
            Boolean retainLogs
    ) {
    }
}

