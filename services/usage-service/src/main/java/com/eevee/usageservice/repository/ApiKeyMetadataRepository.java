package com.eevee.usageservice.repository;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyMetadataEntityId;
import com.eevee.usageservice.domain.ApiKeyMetadataScope;
import com.eevee.usageservice.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApiKeyMetadataRepository extends JpaRepository<ApiKeyMetadataEntity, ApiKeyMetadataEntityId> {

    /**
     * Team-scope rows (one per member per logical key). Callers dedupe by {@code keyId} for filter UIs.
     */
    List<ApiKeyMetadataEntity> findByTeamIdAndId_KeyScopeAndStatusInOrderByUpdatedAtDesc(
            String teamId,
            ApiKeyMetadataScope keyScope,
            Collection<ApiKeyStatus> statuses
    );

    /**
     * Personal-scope keys for the dashboard alias filter: owner matches, no team binding,
     * optionally filtered by provider string (case-insensitive).
     */
    @Query(
            """
                    select m from ApiKeyMetadataEntity m
                    where m.id.userId = :userId
                    and m.id.keyScope = com.eevee.usageservice.domain.ApiKeyMetadataScope.PERSONAL
                    and (m.teamId is null or trim(m.teamId) = '')
                    and (:providerLower is null
                        or (m.provider is not null and lower(trim(m.provider)) = :providerLower))
                    order by m.updatedAt desc
                    """
    )
    List<ApiKeyMetadataEntity> findPersonalKeysForDashboard(
            @Param("userId") String userId,
            @Param("providerLower") String providerLower
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
                    delete from ApiKeyMetadataEntity m
                    where m.id.keyId = :keyId
                    and m.teamId = :teamId
                    and m.id.keyScope = com.eevee.usageservice.domain.ApiKeyMetadataScope.TEAM
                    and m.id.userId not in :currentMembers
                    """
    )
    int deleteTeamMetadataRowsForKeyNotInMemberList(
            @Param("keyId") String keyId,
            @Param("teamId") String teamId,
            @Param("currentMembers") Collection<String> currentMembers
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
                    delete from ApiKeyMetadataEntity m
                    where m.id.keyId = :keyId
                    and m.teamId = :teamId
                    and m.id.keyScope = com.eevee.usageservice.domain.ApiKeyMetadataScope.TEAM
                    """
    )
    int deleteAllTeamMetadataRowsForKey(@Param("keyId") String keyId, @Param("teamId") String teamId);
}
