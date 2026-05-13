package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.BillingTeamApiKeyEventProcessedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingTeamApiKeyEventProcessedRepository
        extends JpaRepository<BillingTeamApiKeyEventProcessedEntity, UUID> {
}

