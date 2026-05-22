package com.eevee.usageservice.service.filter;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyMetadataEntityId;
import com.eevee.usageservice.domain.ApiKeyMetadataScope;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UsageApiKeyFilterResolutionService {

    private final ApiKeyMetadataRepository apiKeyMetadataRepository;
    private final UsageRecordedLogRepository usageRecordedLogRepository;

    public UsageApiKeyFilterResolutionService(
            ApiKeyMetadataRepository apiKeyMetadataRepository,
            UsageRecordedLogRepository usageRecordedLogRepository
    ) {
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
        this.usageRecordedLogRepository = usageRecordedLogRepository;
    }

    public ApiKeyCredentialFilter resolvePersonal(
            String userId,
            String alternatePersonalSubjectUserId,
            String selectedApiKeyId
    ) {
        if (!StringUtils.hasText(selectedApiKeyId)) {
            return ApiKeyCredentialFilter.unrestricted();
        }
        String canonicalId = selectedApiKeyId.trim();
        Set<String> keyIds = new LinkedHashSet<>();
        keyIds.add(canonicalId);
        String keyHash = null;
        for (String owner : personalOwnerIds(userId, alternatePersonalSubjectUserId)) {
            Optional<ApiKeyMetadataEntity> row = apiKeyMetadataRepository.findById(
                    ApiKeyMetadataEntityId.personal(canonicalId, owner)
            );
            if (row.isPresent() && StringUtils.hasText(row.get().getKeyHash())) {
                keyHash = row.get().getKeyHash().trim().toLowerCase(Locale.ROOT);
                break;
            }
        }
        if (StringUtils.hasText(keyHash)) {
            for (String owner : personalOwnerIds(userId, alternatePersonalSubjectUserId)) {
                for (ApiKeyMetadataEntity m : apiKeyMetadataRepository.findPersonalByKeyHash(owner, keyHash)) {
                    keyIds.add(m.getKeyId());
                }
            }
        }
        String fingerprint = resolveFingerprint(keyIds);
        return ApiKeyCredentialFilter.forCredential(keyIds, fingerprint);
    }

    public ApiKeyCredentialFilter resolveTeam(String teamId, String selectedApiKeyId) {
        if (!StringUtils.hasText(selectedApiKeyId) || !StringUtils.hasText(teamId)) {
            return ApiKeyCredentialFilter.unrestricted();
        }
        String canonicalId = selectedApiKeyId.trim();
        String tid = teamId.trim();
        Set<String> keyIds = new LinkedHashSet<>();
        keyIds.add(canonicalId);
        String keyHash = null;
        for (ApiKeyMetadataEntity m : apiKeyMetadataRepository.findTeamByKeyIds(tid, List.of(canonicalId))) {
            if (StringUtils.hasText(m.getKeyHash())) {
                keyHash = m.getKeyHash().trim().toLowerCase(Locale.ROOT);
                break;
            }
        }
        if (StringUtils.hasText(keyHash)) {
            for (ApiKeyMetadataEntity m : apiKeyMetadataRepository.findTeamByKeyHash(tid, keyHash)) {
                keyIds.add(m.getKeyId());
            }
        }
        String fingerprint = resolveFingerprint(keyIds);
        return ApiKeyCredentialFilter.forCredential(keyIds, fingerprint);
    }

    /**
     * Latest non-deleted alias for the same credential as the log row (re-registration display).
     */
    public Optional<String> resolveLatestActiveAliasForLog(
            String userId,
            String teamId,
            String apiKeyId,
            String teamApiKeyId,
            String logFingerprint
    ) {
        String logicalKeyId = StringUtils.hasText(teamApiKeyId) ? teamApiKeyId.trim() : apiKeyId;
        if (!StringUtils.hasText(logicalKeyId)) {
            return Optional.empty();
        }
        boolean teamScope = StringUtils.hasText(teamApiKeyId) && StringUtils.hasText(teamId);
        if (teamScope) {
            return resolveLatestActiveAliasTeam(teamId.trim(), logicalKeyId, logFingerprint);
        }
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        return resolveLatestActiveAliasPersonal(userId.trim(), logicalKeyId, logFingerprint);
    }

    private Optional<String> resolveLatestActiveAliasPersonal(
            String userId,
            String logicalKeyId,
            String logFingerprint
    ) {
        ApiKeyMetadataEntity anchor = apiKeyMetadataRepository.findById(
                ApiKeyMetadataEntityId.personal(logicalKeyId, userId)
        ).orElse(null);
        String keyHash = anchor != null && StringUtils.hasText(anchor.getKeyHash())
                ? anchor.getKeyHash().trim().toLowerCase(Locale.ROOT)
                : null;
        if (StringUtils.hasText(keyHash)) {
            return apiKeyMetadataRepository.findPersonalByKeyHash(userId, keyHash).stream()
                    .filter(m -> m.getStatus() == ApiKeyStatus.ACTIVE || m.getStatus() == ApiKeyStatus.DELETION_REQUESTED)
                    .filter(m -> StringUtils.hasText(m.getAlias()))
                    .max(java.util.Comparator.comparing(ApiKeyMetadataEntity::getUpdatedAt))
                    .map(m -> m.getAlias().trim());
        }
        if (StringUtils.hasText(logFingerprint)) {
            Map<String, String> fps = loadFingerprints(Set.of(logicalKeyId));
            String fp = fps.getOrDefault(logicalKeyId, logFingerprint);
            return findActiveAliasByFingerprintPersonal(userId, fp);
        }
        if (anchor != null && StringUtils.hasText(anchor.getAlias())
                && anchor.getStatus() != ApiKeyStatus.DELETED) {
            return Optional.of(anchor.getAlias().trim());
        }
        return Optional.empty();
    }

    private Optional<String> resolveLatestActiveAliasTeam(
            String teamId,
            String logicalKeyId,
            String logFingerprint
    ) {
        List<ApiKeyMetadataEntity> anchorRows = apiKeyMetadataRepository.findTeamByKeyIds(teamId, List.of(logicalKeyId));
        String keyHash = anchorRows.stream()
                .map(ApiKeyMetadataEntity::getKeyHash)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .orElse(null);
        if (StringUtils.hasText(keyHash)) {
            return apiKeyMetadataRepository.findTeamByKeyHash(teamId, keyHash).stream()
                    .filter(m -> m.getStatus() == ApiKeyStatus.ACTIVE || m.getStatus() == ApiKeyStatus.DELETION_REQUESTED)
                    .filter(m -> StringUtils.hasText(m.getAlias()))
                    .max(java.util.Comparator.comparing(ApiKeyMetadataEntity::getUpdatedAt))
                    .map(m -> m.getAlias().trim());
        }
        return anchorRows.stream()
                .filter(m -> m.getStatus() != ApiKeyStatus.DELETED)
                .filter(m -> StringUtils.hasText(m.getAlias()))
                .max(java.util.Comparator.comparing(ApiKeyMetadataEntity::getUpdatedAt))
                .map(m -> m.getAlias().trim());
    }

    private Optional<String> findActiveAliasByFingerprintPersonal(String userId, String fingerprint) {
        if (!StringUtils.hasText(fingerprint)) {
            return Optional.empty();
        }
        for (ApiKeyMetadataEntity m : apiKeyMetadataRepository.findPersonalKeysForDashboard(userId, null)) {
            if (!StringUtils.hasText(m.getKeyHash()) || !fingerprint.equalsIgnoreCase(m.getKeyHash())) {
                continue;
            }
            if ((m.getStatus() == ApiKeyStatus.ACTIVE || m.getStatus() == ApiKeyStatus.DELETION_REQUESTED)
                    && StringUtils.hasText(m.getAlias())) {
                return Optional.of(m.getAlias().trim());
            }
        }
        return Optional.empty();
    }

    private String resolveFingerprint(Set<String> keyIds) {
        Map<String, String> fps = loadFingerprints(keyIds);
        return fps.values().stream().filter(StringUtils::hasText).findFirst().orElse(null);
    }

    private Map<String, String> loadFingerprints(Set<String> keyIds) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Object[] row : usageRecordedLogRepository.findFingerprintsByApiKeyIds(keyIds)) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                out.putIfAbsent(String.valueOf(row[0]), String.valueOf(row[1]));
            }
        }
        return out;
    }

    private static Set<String> personalOwnerIds(String userId, String alternatePersonalSubjectUserId) {
        Set<String> owners = new LinkedHashSet<>();
        if (StringUtils.hasText(userId)) {
            owners.add(userId.trim());
        }
        if (StringUtils.hasText(alternatePersonalSubjectUserId)) {
            String alt = alternatePersonalSubjectUserId.trim();
            if (!owners.contains(alt)) {
                owners.add(alt);
            }
        }
        return owners;
    }
}
