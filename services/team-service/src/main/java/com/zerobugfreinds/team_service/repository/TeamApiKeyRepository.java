package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TeamApiKeyRepository extends JpaRepository<TeamApiKeyEntity, Long> {
    List<TeamApiKeyEntity> findAllByTeamIdOrderByCreatedAtDesc(Long teamId);
    List<TeamApiKeyEntity> findAllByTeamIdInOrderByTeamIdAscCreatedAtDesc(List<Long> teamIds);

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

    boolean existsByTeamId(Long teamId);

    Optional<TeamApiKeyEntity> findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
            Long teamId,
            TeamApiKeyProvider provider
    );

    Optional<TeamApiKeyEntity> findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(
            Long id,
            Long teamId,
            TeamApiKeyProvider provider
    );

    Optional<TeamApiKeyEntity> findFirstByTeamIdAndProviderAndKeyAliasAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
            Long teamId,
            TeamApiKeyProvider provider,
            String keyAlias
    );

    /**
     * Proxy 등 내부 호출에서 teamId 없이 (provider, keyHash) 만으로 역조회할 때 사용한다.
     * 유일 제약은 (team_id, provider, key_hash) 이므로 서로 다른 팀이 동일한 외부 키를
     * 등록한 경우 2건 이상 조회될 수 있다. 호출자에서 결과 건수를 검증한다.
     */
    List<TeamApiKeyEntity> findAllByProviderAndKeyHash(TeamApiKeyProvider provider, String keyHash);

    List<TeamApiKeyEntity> findAllByPermanentDeletionAtIsNotNullAndPermanentDeletionAtBefore(Instant now);
}
