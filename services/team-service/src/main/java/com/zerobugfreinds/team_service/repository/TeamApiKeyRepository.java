package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamApiKeyRepository extends JpaRepository<TeamApiKeyEntity, Long> {
    List<TeamApiKeyEntity> findAllByTeamIdOrderByCreatedAtDesc(Long teamId);

    Optional<TeamApiKeyEntity> findByIdAndTeamId(Long id, Long teamId);

    Optional<TeamApiKeyEntity> findByTeamIdAndProviderAndKeyHash(
            Long teamId,
            TeamApiKeyProvider provider,
            String keyHash
    );

    Optional<TeamApiKeyEntity> findByTeamIdAndProviderAndKeyHashAndIdNot(
            Long teamId,
            TeamApiKeyProvider provider,
            String keyHash,
            Long id
    );

    boolean existsByTeamIdAndProviderAndKeyHash(Long teamId, TeamApiKeyProvider provider, String keyHash);

    boolean existsByTeamIdAndKeyAlias(Long teamId, String keyAlias);

    boolean existsByTeamIdAndKeyAliasAndIdNot(Long teamId, String keyAlias, Long id);

    boolean existsByTeamIdAndProviderAndKeyHashAndIdNot(
            Long teamId,
            TeamApiKeyProvider provider,
            String keyHash,
            Long id
    );
}
