package com.eevee.proxyservice.key.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from team-service trusted credential API (ext fingerprint hydration).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamKeyCredentialResponse(
        @JsonProperty("plainKey") String plainKey,
        @JsonProperty("keyId") String keyId
) {
}
