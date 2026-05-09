package com.eevee.usageservice.repository;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ApiKeyMetadataRepository extends JpaRepository<ApiKeyMetadataEntity, String> {
    List<ApiKeyMetadataEntity> findByTeamIdAndStatusInOrderByUpdatedAtAsc(String teamId, Collection<ApiKeyStatus> statuses);
}
