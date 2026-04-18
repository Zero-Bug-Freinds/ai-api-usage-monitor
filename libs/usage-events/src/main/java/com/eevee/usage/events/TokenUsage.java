package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Provider-neutral token counts (maps to usage_log concept in architecture docs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenUsage(
        String model,
        @JsonAlias({"prompt_tokens"}) Long promptTokens,
        @JsonAlias({"completion_tokens"}) Long completionTokens,
        @JsonAlias({"total_tokens"}) Long totalTokens,
        // OpenAI-only nested token breakdown (nullable for other providers)
        @JsonAlias({"prompt_cached_tokens"}) Long promptCachedTokens,
        @JsonAlias({"prompt_audio_tokens"}) Long promptAudioTokens,
        @JsonAlias({"completion_reasoning_tokens"}) Long completionReasoningTokens,
        @JsonAlias({"completion_audio_tokens"}) Long completionAudioTokens,
        @JsonAlias({"completion_accepted_prediction_tokens"}) Long completionAcceptedPredictionTokens,
        @JsonAlias({"completion_rejected_prediction_tokens"}) Long completionRejectedPredictionTokens
) {
}
