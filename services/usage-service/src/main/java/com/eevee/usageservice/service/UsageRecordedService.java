package com.eevee.usageservice.service;

import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UsageRecordedService {

    private static final Logger log = LoggerFactory.getLogger(UsageRecordedService.class);

    private final UsageRecordedLogRepository repository;

    public UsageRecordedService(UsageRecordedLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void persist(UsageRecordedEvent event) {
        if (repository.existsByEventId(event.eventId())) {
            log.debug("Skipping duplicate usage event eventId={}", event.eventId());
            return;
        }
        UsageRecordedLogEntity entity = map(event);
        repository.save(entity);
        log.debug("Stored usage event eventId={} userId={}", event.eventId(), event.userId());
    }

    private static UsageRecordedLogEntity map(UsageRecordedEvent event) {
        TokenUsage tu = event.tokenUsage();
        String model = event.model();
        Long prompt = null;
        Long completion = null;
        Long total = null;
        if (tu != null) {
            if (model == null || model.isBlank()) {
                model = tu.model();
            }
            prompt = tu.promptTokens();
            completion = tu.completionTokens();
            total = tu.totalTokens();
        }
        boolean successful = Boolean.TRUE.equals(event.requestSuccessful());
        return new UsageRecordedLogEntity(
                event.eventId(),
                event.occurredAt(),
                event.correlationId(),
                event.userId(),
                event.organizationId(),
                event.teamId(),
                event.apiKeyId(),
                event.apiKeyFingerprint(),
                event.apiKeySource(),
                event.provider(),
                model,
                prompt,
                completion,
                total,
                event.estimatedCost(),
                event.requestPath(),
                event.upstreamHost(),
                event.streaming(),
                successful,
                event.upstreamStatusCode(),
                Instant.now()
        );
    }
}
