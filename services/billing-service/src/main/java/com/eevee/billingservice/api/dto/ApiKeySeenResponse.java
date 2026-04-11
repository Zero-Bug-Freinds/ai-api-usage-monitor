package com.eevee.billingservice.api.dto;

import com.eevee.usage.events.AiProvider;

import java.time.Instant;

public record ApiKeySeenResponse(String apiKeyId, AiProvider provider, Instant firstSeenAt) {
}
