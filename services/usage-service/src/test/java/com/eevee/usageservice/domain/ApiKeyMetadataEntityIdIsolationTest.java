package com.eevee.usageservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: identity key id and team_api_key id can collide numerically; composite id must still distinguish rows.
 */
class ApiKeyMetadataEntityIdIsolationTest {

    @Test
    void sameKeyId_personalVsTeam_differByScope() {
        assertThat(ApiKeyMetadataEntityId.personal("123", "user-a"))
                .isNotEqualTo(ApiKeyMetadataEntityId.team("123", "user-a"));
    }

    @Test
    void sameKeyIdAndScope_teamRowsDifferByMemberUserId() {
        assertThat(ApiKeyMetadataEntityId.team("123", "user-a"))
                .isNotEqualTo(ApiKeyMetadataEntityId.team("123", "user-b"));
    }
}
