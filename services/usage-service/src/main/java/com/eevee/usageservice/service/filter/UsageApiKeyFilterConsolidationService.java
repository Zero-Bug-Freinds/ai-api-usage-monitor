package com.eevee.usageservice.service.filter;

import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.api.dto.bff.TeamApiKeyOptionItem;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UsageApiKeyFilterConsolidationService {

    private static final EnumSet<ApiKeyStatus> ACTIVE_LIKE =
            EnumSet.of(ApiKeyStatus.ACTIVE, ApiKeyStatus.DELETION_REQUESTED);

    private final ApiKeyMetadataRepository apiKeyMetadataRepository;
    private final UsageRecordedLogRepository usageRecordedLogRepository;

    public UsageApiKeyFilterConsolidationService(
            ApiKeyMetadataRepository apiKeyMetadataRepository,
            UsageRecordedLogRepository usageRecordedLogRepository
    ) {
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
        this.usageRecordedLogRepository = usageRecordedLogRepository;
    }

    public List<UsageLogApiKeyItemResponse> consolidatePersonal(
            List<UsageLogApiKeyItemResponse> raw,
            String userId,
            String alternatePersonalSubjectUserId
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> owners = personalOwnerIds(userId, alternatePersonalSubjectUserId);
        Set<String> keyIds = raw.stream().map(UsageLogApiKeyItemResponse::apiKeyId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ApiKeyMetadataEntity> metadataByKeyId = new LinkedHashMap<>();
        for (String owner : owners) {
            for (ApiKeyMetadataEntity m : apiKeyMetadataRepository.findPersonalByKeyIds(owner, keyIds)) {
                metadataByKeyId.merge(m.getKeyId(), m, UsageApiKeyFilterConsolidationService::preferMetadataRow);
            }
        }
        Map<String, String> fingerprintByKeyId = loadFingerprintsByKeyId(keyIds);
        List<FilterCandidate> candidates = raw.stream()
                .map(item -> toCandidate(item, metadataByKeyId.get(item.apiKeyId()), fingerprintByKeyId.get(item.apiKeyId())))
                .toList();
        return consolidateCandidates(candidates);
    }

    public List<TeamApiKeyOptionItem> consolidateTeam(
            List<TeamApiKeyOptionItem> raw,
            String teamId
    ) {
        if (raw == null || raw.isEmpty() || !StringUtils.hasText(teamId)) {
            return raw == null ? List.of() : raw;
        }
        String tid = teamId.trim();
        Set<String> keyIds = raw.stream().map(TeamApiKeyOptionItem::id).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ApiKeyMetadataEntity> metadataByKeyId = apiKeyMetadataRepository.findTeamByKeyIds(tid, keyIds).stream()
                .collect(Collectors.toMap(
                        ApiKeyMetadataEntity::getKeyId,
                        m -> m,
                        UsageApiKeyFilterConsolidationService::preferMetadataRow,
                        LinkedHashMap::new
                ));
        Map<String, String> fingerprintByKeyId = loadFingerprintsByKeyId(keyIds);
        Map<String, TeamApiKeyOptionItem> rawById = raw.stream()
                .collect(Collectors.toMap(TeamApiKeyOptionItem::id, i -> i, (a, b) -> a, LinkedHashMap::new));
        List<FilterCandidate> candidates = raw.stream()
                .map(item -> {
                    ApiKeyMetadataEntity meta = metadataByKeyId.get(item.id());
                    ApiKeyStatus status = parseStatus(item.status(), meta);
                    return new FilterCandidate(
                            item.id(),
                            pickAlias(item.alias(), meta),
                            status,
                            credentialGroupKey(meta, fingerprintByKeyId.get(item.id()), item.id(), item.provider()),
                            pickUpdatedAt(item.updatedAt(), meta)
                    );
                })
                .toList();
        List<UsageLogApiKeyItemResponse> consolidated = consolidateCandidates(candidates);
        return consolidated.stream()
                .map(c -> {
                    TeamApiKeyOptionItem orig = rawById.get(c.apiKeyId());
                    return new TeamApiKeyOptionItem(
                            c.apiKeyId(),
                            c.alias(),
                            orig.provider(),
                            orig.updatedAt(),
                            c.status() != null ? c.status().name() : orig.status()
                    );
                })
                .toList();
    }

    private List<UsageLogApiKeyItemResponse> consolidateCandidates(List<FilterCandidate> candidates) {
        Map<String, List<FilterCandidate>> groups = new LinkedHashMap<>();
        for (FilterCandidate c : candidates) {
            groups.computeIfAbsent(c.credentialGroupKey(), k -> new ArrayList<>()).add(c);
        }
        List<UsageLogApiKeyItemResponse> out = new ArrayList<>();
        for (List<FilterCandidate> group : groups.values()) {
            FilterCandidate active = pickCanonicalActive(group);
            if (active != null) {
                out.add(toResponse(active));
                continue;
            }
            FilterCandidate deleted = pickCanonicalDeleted(group);
            if (deleted != null) {
                out.add(toResponse(deleted));
                continue;
            }
            FilterCandidate fallback = pickCanonicalUnknown(group);
            if (fallback != null) {
                out.add(toResponse(fallback));
            }
        }
        return List.copyOf(out);
    }

    private static FilterCandidate pickCanonicalActive(List<FilterCandidate> group) {
        return group.stream()
                .filter(c -> c.status() == null || ACTIVE_LIKE.contains(c.status()))
                .max(Comparator.comparingInt(UsageApiKeyFilterConsolidationService::aliasScore)
                        .thenComparing(FilterCandidate::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static FilterCandidate pickCanonicalDeleted(List<FilterCandidate> group) {
        return group.stream()
                .filter(c -> c.status() == ApiKeyStatus.DELETED)
                .max(Comparator.comparing(FilterCandidate::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static FilterCandidate pickCanonicalUnknown(List<FilterCandidate> group) {
        return group.stream()
                .max(Comparator.comparingInt(UsageApiKeyFilterConsolidationService::aliasScore)
                        .thenComparing(FilterCandidate::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static UsageLogApiKeyItemResponse toResponse(FilterCandidate c) {
        return new UsageLogApiKeyItemResponse(c.apiKeyId(), c.alias(), c.status());
    }

    private FilterCandidate toCandidate(
            UsageLogApiKeyItemResponse item,
            ApiKeyMetadataEntity meta,
            String fingerprint
    ) {
        ApiKeyStatus status = item.status() != null ? item.status() : (meta != null ? meta.getStatus() : null);
        return new FilterCandidate(
                item.apiKeyId(),
                pickAlias(item.alias(), meta),
                status,
                credentialGroupKey(meta, fingerprint, item.apiKeyId(), meta != null ? meta.getProvider() : null),
                pickUpdatedAt(null, meta)
        );
    }

    private static String pickAlias(String itemAlias, ApiKeyMetadataEntity meta) {
        if (StringUtils.hasText(itemAlias)) {
            return itemAlias.trim();
        }
        if (meta != null && StringUtils.hasText(meta.getAlias())) {
            return meta.getAlias().trim();
        }
        return null;
    }

    private static Instant pickUpdatedAt(Instant itemUpdatedAt, ApiKeyMetadataEntity meta) {
        if (itemUpdatedAt != null) {
            return itemUpdatedAt;
        }
        return meta != null ? meta.getUpdatedAt() : null;
    }

    private static ApiKeyStatus parseStatus(String status, ApiKeyMetadataEntity meta) {
        if (StringUtils.hasText(status)) {
            try {
                return ApiKeyStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return meta != null ? meta.getStatus() : null;
    }

    private static String credentialGroupKey(
            ApiKeyMetadataEntity meta,
            String fingerprint,
            String apiKeyId,
            String provider
    ) {
        if (meta != null && StringUtils.hasText(meta.getKeyHash())) {
            String prov = StringUtils.hasText(meta.getProvider()) ? meta.getProvider().trim().toLowerCase(Locale.ROOT) : "";
            return "hash:" + prov + ":" + meta.getKeyHash().trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.hasText(fingerprint)) {
            return "fp:" + fingerprint.trim();
        }
        return "id:" + apiKeyId;
    }

    private Map<String, String> loadFingerprintsByKeyId(Collection<String> keyIds) {
        if (keyIds == null || keyIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
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

    private static ApiKeyMetadataEntity preferMetadataRow(ApiKeyMetadataEntity a, ApiKeyMetadataEntity b) {
        int sa = metadataRichnessScore(a);
        int sb = metadataRichnessScore(b);
        if (sa != sb) {
            return sa > sb ? a : b;
        }
        if (a.getUpdatedAt() == null) {
            return b;
        }
        if (b.getUpdatedAt() == null) {
            return a;
        }
        return a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a : b;
    }

    private static int metadataRichnessScore(ApiKeyMetadataEntity m) {
        int score = 0;
        if (StringUtils.hasText(m.getKeyHash())) {
            score += 4;
        }
        if (StringUtils.hasText(m.getProvider())) {
            score += 2;
        }
        if (StringUtils.hasText(m.getAlias())) {
            score += 1;
        }
        return score;
    }

    private static int aliasScore(FilterCandidate c) {
        return StringUtils.hasText(c.alias()) ? 1 : 0;
    }

    private record FilterCandidate(
            String apiKeyId,
            String alias,
            ApiKeyStatus status,
            String credentialGroupKey,
            Instant updatedAt
    ) {
    }
}
