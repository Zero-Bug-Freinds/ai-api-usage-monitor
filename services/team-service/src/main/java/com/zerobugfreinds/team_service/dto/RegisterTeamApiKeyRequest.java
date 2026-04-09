package com.zerobugfreinds.team_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterTeamApiKeyRequest(
        @NotNull(message = "provider는 필수입니다")
        TeamApiKeyProvider provider,

        @NotBlank(message = "externalKey는 필수입니다")
        @Size(max = 4096)
        @JsonProperty("externalKey")
        String externalKey,

        @NotBlank(message = "alias는 필수입니다")
        @Size(max = 100)
        @JsonProperty("alias")
        String alias
) {
}
