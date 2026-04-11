package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.BillingProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingProcessedEventRepository extends JpaRepository<BillingProcessedEventEntity, UUID> {
}
