package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.usage.events.AiProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProviderModelPriceRepository extends JpaRepository<ProviderModelPriceEntity, Long> {

    @Query("""
            select p from ProviderModelPriceEntity p
            where p.provider = :provider and p.model = :model
            and p.validFrom <= :at
            and (p.validTo is null or p.validTo > :at)
            order by p.validFrom desc
            """)
    List<ProviderModelPriceEntity> findActivePrices(
            @Param("provider") AiProvider provider,
            @Param("model") String model,
            @Param("at") Instant at,
            Pageable pageable
    );
}
