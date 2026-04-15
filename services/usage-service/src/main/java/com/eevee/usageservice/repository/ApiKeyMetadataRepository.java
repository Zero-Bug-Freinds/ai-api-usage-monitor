package com.eevee.usageservice.repository;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyMetadataRepository extends JpaRepository<ApiKeyMetadataEntity, String> {
}
