package com.eevee.usageservice.service.bff.team;

import com.eevee.usageservice.api.dto.bff.TeamApiKeyOptionItem;
import com.eevee.usageservice.api.dto.bff.TeamSummaryOptionItem;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyMetadataScope;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeamBffQueryService {
    private static final List<ApiKeyStatus> VISIBLE_TEAM_KEY_STATUSES = List.of(
            ApiKeyStatus.ACTIVE,
            ApiKeyStatus.DELETION_REQUESTED
    );

    private final TeamServiceClient teamServiceClient;
    private final ApiKeyMetadataRepository apiKeyMetadataRepository;

    public TeamBffQueryService(
            TeamServiceClient teamServiceClient,
            ApiKeyMetadataRepository apiKeyMetadataRepository
    ) {
        this.teamServiceClient = teamServiceClient;
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamSummaryOptionItem> loadTeams(String requesterUserId, String fallbackPlatformUserId) {
        return teamServiceClient.fetchUserTeams(requesterUserId, fallbackPlatformUserId).stream()
                .map(team -> new TeamSummaryOptionItem(team.id(), team.name(), team.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamApiKeyOptionItem> loadTeamApiKeys(String requesterUserId, String teamId) {
        if (!StringUtils.hasText(requesterUserId)) {
            throw new IllegalArgumentException("requester userId is required");
        }
        if (!StringUtils.hasText(teamId)) {
            return List.of();
        }
        List<ApiKeyMetadataEntity> rows = apiKeyMetadataRepository
                .findByTeamIdAndId_KeyScopeAndStatusInOrderByUpdatedAtDesc(
                        teamId.trim(),
                        ApiKeyMetadataScope.TEAM,
                        VISIBLE_TEAM_KEY_STATUSES
                );
        Map<String, ApiKeyMetadataEntity> byLogicalKey = new LinkedHashMap<>();
        for (ApiKeyMetadataEntity m : rows) {
            byLogicalKey.putIfAbsent(m.getKeyId(), m);
        }
        return byLogicalKey.values().stream()
                .sorted((a, b) -> a.getKeyId().compareTo(b.getKeyId()))
                .map(entity -> new TeamApiKeyOptionItem(
                        entity.getKeyId(),
                        entity.getAlias(),
                        entity.getProvider(),
                        entity.getUpdatedAt()
                ))
                .toList();
    }
}
