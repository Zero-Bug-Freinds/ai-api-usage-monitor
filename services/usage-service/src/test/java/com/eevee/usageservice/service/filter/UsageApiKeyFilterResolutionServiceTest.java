package com.eevee.usageservice.service.filter;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyMetadataEntityId;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageApiKeyFilterResolutionServiceTest {

    @Mock
    private ApiKeyMetadataRepository apiKeyMetadataRepository;

    @Mock
    private UsageRecordedLogRepository usageRecordedLogRepository;

    private UsageApiKeyFilterResolutionService service;

    @BeforeEach
    void setUp() {
        service = new UsageApiKeyFilterResolutionService(apiKeyMetadataRepository, usageRecordedLogRepository);
    }

    @Test
    void resolvePersonal_includesAllKeyIdsWithSameHash() {
        String hash = "deadbeef";
        ApiKeyMetadataEntity canonical = ApiKeyMetadataEntity.createPersonal("new-id", "u1");
        canonical.apply(null, "OPENAI", "alias", ApiKeyStatus.ACTIVE, Instant.now(), hash);
        ApiKeyMetadataEntity legacy = ApiKeyMetadataEntity.createPersonal("old-id", "u1");
        legacy.apply(null, "OPENAI", null, ApiKeyStatus.DELETED, Instant.now(), hash);

        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.personal("new-id", "u1")))
                .thenReturn(Optional.of(canonical));
        when(apiKeyMetadataRepository.findPersonalByKeyHash("u1", hash)).thenReturn(List.of(canonical, legacy));
        when(usageRecordedLogRepository.findFingerprintsByApiKeyIds(any())).thenReturn(List.of());

        ApiKeyCredentialFilter filter = service.resolvePersonal("u1", null, "new-id");

        assertThat(filter.isRestricted()).isTrue();
        assertThat(filter.apiKeyIds()).containsExactlyInAnyOrder("new-id", "old-id");
    }

    @Test
    void resolveLatestActiveAliasForLog_usesActiveRowWhenLogPointsToDeleted() {
        String hash = "cafebabe";
        ApiKeyMetadataEntity deleted = ApiKeyMetadataEntity.createPersonal("old-id", "u1");
        deleted.apply(null, "OPENAI", "old", ApiKeyStatus.DELETED, Instant.parse("2025-01-01T00:00:00Z"), hash);
        ApiKeyMetadataEntity active = ApiKeyMetadataEntity.createPersonal("new-id", "u1");
        active.apply(null, "OPENAI", "최신 별칭", ApiKeyStatus.ACTIVE, Instant.parse("2025-06-01T00:00:00Z"), hash);

        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.personal("old-id", "u1")))
                .thenReturn(Optional.of(deleted));
        when(apiKeyMetadataRepository.findPersonalByKeyHash("u1", hash)).thenReturn(List.of(deleted, active));

        Optional<String> alias = service.resolveLatestActiveAliasForLog("u1", null, "old-id", null, null);

        assertThat(alias).contains("최신 별칭");
    }
}
