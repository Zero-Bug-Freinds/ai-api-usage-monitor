package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamApiKeyRepository extends JpaRepository<TeamApiKeyEntity, Long> {
    List<TeamApiKeyEntity> findAllByTeamIdOrderByCreatedAtDesc(Long teamId);

    boolean existsByTeamIdAndProviderAndKeyHash(Long teamId, TeamApiKeyProvider provider, String keyHash);

    boolean existsByTeamIdAndKeyAlias(Long teamId, String keyAlias);
}
