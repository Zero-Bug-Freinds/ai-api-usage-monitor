package com.eevee.usageservice.usage;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsageRecordedMetadataScopeTest {

    @Test
    void teamKey_requiresTeamSourceAndNormalizedTeamContext() {
        UsageRecordedEvent fullTeam = new UsageRecordedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-01T00:00:00Z"),
                "c",
                "user@example.com",
                null,
                "1",
                "888",
                "alias",
                "999",
                "fp",
                "team",
                AiProvider.OPENAI,
                "gpt-4o",
                new TokenUsage("gpt-4o", 1L, 1L, 2L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/p",
                "api.openai.com",
                false,
                true,
                200
        );
        assertThat(UsageRecordedMetadataScope.isTeamKeyMetadata(fullTeam)).isTrue();
    }

    @Test
    void managedSource_isNotTeamMetadataEvenWithTeamLikeFields() {
        UsageRecordedEvent managed = new UsageRecordedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-01T00:00:00Z"),
                "c",
                "user@example.com",
                null,
                "1",
                "888",
                "alias",
                "999",
                "fp",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o",
                new TokenUsage("gpt-4o", 1L, 1L, 2L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/p",
                "api.openai.com",
                false,
                true,
                200
        );
        assertThat(UsageRecordedMetadataScope.isTeamKeyMetadata(managed)).isFalse();
    }
}
