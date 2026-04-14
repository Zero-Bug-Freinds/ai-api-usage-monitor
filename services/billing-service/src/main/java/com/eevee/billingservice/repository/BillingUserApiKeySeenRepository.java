package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.BillingUserApiKeySeenEntity;
import com.eevee.billingservice.domain.BillingUserApiKeySeenId;
import com.eevee.usage.events.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BillingUserApiKeySeenRepository extends JpaRepository<BillingUserApiKeySeenEntity, BillingUserApiKeySeenId> {

    List<BillingUserApiKeySeenEntity> findByIdUserIdOrderByIdApiKeyIdAsc(String userId);

    List<BillingUserApiKeySeenEntity> findByIdUserIdAndIdProviderOrderByIdApiKeyIdAsc(String userId, AiProvider provider);
}
