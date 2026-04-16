package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Provider-neutral token counts (maps to usage_log concept in architecture docs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenUsage(
        String model,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        // OpenAI-only nested token breakdown (nullable for other providers)
        Long promptCachedTokens,
        Long promptAudioTokens,
        Long completionReasoningTokens,
        Long completionAudioTokens,
        Long completionAcceptedPredictionTokens,
        Long completionRejectedPredictionTokens
) {
}
