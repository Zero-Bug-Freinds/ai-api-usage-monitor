package com.eevee.usageservice.service.filter;

import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageApiKeyFilterConsolidationServiceTest {

    @Mock
    private ApiKeyMetadataRepository apiKeyMetadataRepository;

    @Mock
    private UsageRecordedLogRepository usageRecordedLogRepository;

    private UsageApiKeyFilterConsolidationService service;

    @BeforeEach
    void setUp() {
        service = new UsageApiKeyFilterConsolidationService(apiKeyMetadataRepository, usageRecordedLogRepository);
    }

    @Test
    void consolidatePersonal_sameKeyHash_returnsSingleActiveWithLatestAlias() {
        String hash = "abc123";
        ApiKeyMetadataEntity deleted = ApiKeyMetadataEntity.createPersonal("old-id", "u1");
        deleted.apply(null, "OPENAI", null, ApiKeyStatus.DELETED, Instant.parse("2025-01-01T00:00:00Z"), hash);
        ApiKeyMetadataEntity active = ApiKeyMetadataEntity.createPersonal("new-id", "u1");
        active.apply(null, "OPENAI", "재등록 별칭", ApiKeyStatus.ACTIVE, Instant.parse("2025-06-01T00:00:00Z"), hash);

        when(apiKeyMetadataRepository.findPersonalByKeyIds(eq("u1"), any())).thenReturn(List.of(deleted, active));
        when(usageRecordedLogRepository.findFingerprintsByApiKeyIds(any())).thenReturn(List.of());

        List<UsageLogApiKeyItemResponse> raw = List.of(
                new UsageLogApiKeyItemResponse("old-id", null, ApiKeyStatus.DELETED),
                new UsageLogApiKeyItemResponse("new-id", "재등록 별칭", ApiKeyStatus.ACTIVE)
        );

        List<UsageLogApiKeyItemResponse> out = service.consolidatePersonal(raw, "u1", null);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().apiKeyId()).isEqualTo("new-id");
        assertThat(out.getFirst().alias()).isEqualTo("재등록 별칭");
        assertThat(out.getFirst().status()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    void consolidatePersonal_nullStatusStub_absorbedWhenActiveExists() {
        when(apiKeyMetadataRepository.findPersonalByKeyIds(eq("u1"), any())).thenReturn(List.of());
        when(usageRecordedLogRepository.findFingerprintsByApiKeyIds(any())).thenReturn(List.of());

        List<UsageLogApiKeyItemResponse> raw = List.of(
                new UsageLogApiKeyItemResponse("k1", null, null),
                new UsageLogApiKeyItemResponse("k1", "별칭", ApiKeyStatus.ACTIVE)
        );

        List<UsageLogApiKeyItemResponse> out = service.consolidatePersonal(raw, "u1", null);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().alias()).isEqualTo("별칭");
    }
}
