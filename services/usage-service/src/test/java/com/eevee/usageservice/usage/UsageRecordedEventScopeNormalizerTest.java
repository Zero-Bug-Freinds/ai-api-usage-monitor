package com.eevee.usageservice.usage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsageRecordedEventScopeNormalizerTest {

    @Test
    void normalizeTeamApiKeyId_nullWhenTeamIdMissing_evenIfTeamKeyPresent() {
        assertThat(UsageRecordedEventScopeNormalizer.normalizeTeamApiKeyId("99", null)).isNull();
    }

    @Test
    void normalizeTeamApiKeyId_trimsWhenTeamPresent() {
        assertThat(UsageRecordedEventScopeNormalizer.normalizeTeamApiKeyId("  42  ", "1")).isEqualTo("42");
    }

    @Test
    void normalizeTeamId_blankBecomesNull() {
        assertThat(UsageRecordedEventScopeNormalizer.normalizeTeamId("   ")).isNull();
    }
}
