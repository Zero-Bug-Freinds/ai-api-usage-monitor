package com.eevee.usageservice.api.dto.bff;

import java.util.List;

public record TeamApiKeyOptionResponse(
        List<TeamApiKeyOptionItem> apiKeys
) {
}
