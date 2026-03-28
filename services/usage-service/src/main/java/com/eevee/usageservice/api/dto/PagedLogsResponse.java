package com.eevee.usageservice.api.dto;

import java.util.List;

public record PagedLogsResponse(
        List<UsageLogEntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}