package com.eevee.usageservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@link UsageRecordedLogEntity} join formula literals stay aligned with JPA {@code STRING} storage for
 * {@link ApiKeyMetadataScope}.
 */
class ApiKeyMetadataScopeJoinLiteralTest {

    @Test
    void joinFormulaLiteralsMatchEnumNames() {
        assertThat(ApiKeyMetadataScope.PERSONAL.name()).isEqualTo("PERSONAL");
        assertThat(ApiKeyMetadataScope.TEAM.name()).isEqualTo("TEAM");
    }
}
