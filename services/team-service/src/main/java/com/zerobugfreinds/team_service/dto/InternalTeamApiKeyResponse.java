package com.zerobugfreinds.team_service.dto;

public record InternalTeamApiKeyResponse(
        String plainKey,
        String keyId
) {
}
