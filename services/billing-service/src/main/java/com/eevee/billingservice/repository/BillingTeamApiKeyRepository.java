package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.BillingTeamApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingTeamApiKeyRepository extends JpaRepository<BillingTeamApiKeyEntity, Long> {
    List<BillingTeamApiKeyEntity> findByTeamId(Long teamId);
}

