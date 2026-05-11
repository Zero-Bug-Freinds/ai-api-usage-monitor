package com.eevee.usageservice.repository;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApiKeyMetadataRepository extends JpaRepository<ApiKeyMetadataEntity, String> {
    List<ApiKeyMetadataEntity> findByTeamIdAndStatusInOrderByUpdatedAtAsc(String teamId, Collection<ApiKeyStatus> statuses);

    /**
     * Personal-scope keys for the dashboard alias filter: owner matches, no team binding,
     * optionally filtered by provider string (case-insensitive). Newest metadata updates first
     * (covers newly registered keys; {@link ApiKeyMetadataEntity} has no separate createdAt).
     */
    @Query(
            """
                    select m from ApiKeyMetadataEntity m
                    where m.userId = :userId
                    and (m.teamId is null or trim(m.teamId) = '')
                    and (:providerStr is null
                        or lower(trim(coalesce(m.provider, ''))) = lower(trim(:providerStr)))
                    order by m.updatedAt desc
                    """
    )
    List<ApiKeyMetadataEntity> findPersonalKeysForDashboard(
            @Param("userId") String userId,
            @Param("providerStr") String providerStr
    );
}
