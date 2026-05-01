package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.BillingCostCorrectionProcessedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingCostCorrectionProcessedRepository extends JpaRepository<BillingCostCorrectionProcessedEntity, UUID> {
}
