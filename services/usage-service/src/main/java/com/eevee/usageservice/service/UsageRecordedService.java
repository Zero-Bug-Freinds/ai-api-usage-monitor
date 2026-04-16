package com.eevee.usageservice.service;

import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UsageRecordedService {

    private static final Logger log = LoggerFactory.getLogger(UsageRecordedService.class);

    private final UsageRecordedLogRepository repository;
    private final ObjectMapper objectMapper;

    public UsageRecordedService(UsageRecordedLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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

    private UsageRecordedLogEntity map(UsageRecordedEvent event) {
        TokenUsage tu = event.tokenUsage();
        String model = event.model();
        Long prompt = null;
        Long completion = null;
        Long total = null;
        Long promptCachedTokens = null;
        Long promptAudioTokens = null;
        Long completionReasoningTokens = null;
        Long completionAudioTokens = null;
        Long completionAcceptedPredictionTokens = null;
        Long completionRejectedPredictionTokens = null;
        if (tu != null) {
            if (model == null || model.isBlank()) {
                model = tu.model();
            }
            prompt = tu.promptTokens();
            completion = tu.completionTokens();
            total = tu.totalTokens();
            promptCachedTokens = tu.promptCachedTokens();
            promptAudioTokens = tu.promptAudioTokens();
            completionReasoningTokens = tu.completionReasoningTokens();
            completionAudioTokens = tu.completionAudioTokens();
            completionAcceptedPredictionTokens = tu.completionAcceptedPredictionTokens();
            completionRejectedPredictionTokens = tu.completionRejectedPredictionTokens();
        }
        Long estimatedReasoningTokens;
        if (event.provider() == AiProvider.OPENAI && tu != null) {
            // OpenAI: reasoning token breakdown is provided in response.
            estimatedReasoningTokens =
                    safeLong(completionReasoningTokens)
                            + safeLong(completionAudioTokens)
                            + safeLong(completionAcceptedPredictionTokens)
                            + safeLong(completionRejectedPredictionTokens);
        } else {
            // Google/Claude: estimate derived from total - input - output.
            estimatedReasoningTokens = estimateReasoningTokens(total, prompt, completion);
        }
        String providerTokenDetailsJson = buildProviderTokenDetailsJson(event.provider(), promptCachedTokens, promptAudioTokens,
                completionReasoningTokens, completionAudioTokens,
                completionAcceptedPredictionTokens, completionRejectedPredictionTokens);
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
                estimatedReasoningTokens,
                providerTokenDetailsJson,
                event.estimatedCost(),
                event.requestPath(),
                event.upstreamHost(),
                event.streaming(),
                successful,
                event.upstreamStatusCode(),
                Instant.now()
        );
    }

    private String buildProviderTokenDetailsJson(AiProvider provider,
                                                 Long promptCachedTokens,
                                                 Long promptAudioTokens,
                                                 Long completionReasoningTokens,
                                                 Long completionAudioTokens,
                                                 Long completionAcceptedPredictionTokens,
                                                 Long completionRejectedPredictionTokens) {
        if (provider != AiProvider.OPENAI) {
            return null;
        }
        var map = new java.util.LinkedHashMap<String, Long>();
        if (promptCachedTokens != null) {
            map.put("prompt_cached_tokens", promptCachedTokens);
        }
        if (promptAudioTokens != null) {
            map.put("prompt_audio_tokens", promptAudioTokens);
        }
        if (completionReasoningTokens != null) {
            map.put("completion_reasoning_tokens", completionReasoningTokens);
        }
        if (completionAudioTokens != null) {
            map.put("completion_audio_tokens", completionAudioTokens);
        }
        if (completionAcceptedPredictionTokens != null) {
            map.put("completion_accepted_prediction_tokens", completionAcceptedPredictionTokens);
        }
        if (completionRejectedPredictionTokens != null) {
            map.put("completion_rejected_prediction_tokens", completionRejectedPredictionTokens);
        }
        if (map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize provider_token_details, skipping. provider={}", provider, e);
            return null;
        }
    }

    private static long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private static Long estimateReasoningTokens(Long totalTokens, Long promptTokens, Long completionTokens) {
        if (totalTokens == null || promptTokens == null || completionTokens == null) {
            return null;
        }
        long reasoning = totalTokens - promptTokens - completionTokens;
        return Math.max(reasoning, 0L);
    }
}
