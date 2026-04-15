package com.eevee.usageservice.api.dto;

import com.eevee.usageservice.domain.ApiKeyStatus;

public record UsageLogApiKeyItemResponse(
        String apiKeyId,
        String alias,
        ApiKeyStatus status
) {
}
