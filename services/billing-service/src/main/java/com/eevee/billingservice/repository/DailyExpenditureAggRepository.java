package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.DailyExpenditureAggEntity;
import com.eevee.billingservice.domain.DailyExpenditureAggId;
import com.eevee.usage.events.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DailyExpenditureAggRepository extends JpaRepository<DailyExpenditureAggEntity, DailyExpenditureAggId> {

    @Query("""
            select coalesce(sum(d.totalCostUsd), 0)
            from DailyExpenditureAggEntity d
            where d.id.userId = :userId and d.id.apiKeyId = :apiKeyId and d.id.provider = :provider
            and d.id.aggDate >= :fromInclusive and d.id.aggDate <= :toInclusive
            """)
    BigDecimal sumTotalCostUsd(
            @Param("userId") String userId,
            @Param("apiKeyId") String apiKeyId,
            @Param("provider") AiProvider provider,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );

    @Query("""
            select coalesce(sum(d.totalCostUsd), 0)
            from DailyExpenditureAggEntity d
            where d.id.userId = :userId
            and d.id.aggDate >= :fromInclusive and d.id.aggDate <= :toInclusive
            """)
    BigDecimal sumTotalCostUsdForUser(
            @Param("userId") String userId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );

    @Query("""
            select d.id.aggDate, sum(d.totalCostUsd)
            from DailyExpenditureAggEntity d
            where d.id.userId = :userId and d.id.apiKeyId = :apiKeyId and d.id.provider = :provider
            and d.id.aggDate >= :fromInclusive and d.id.aggDate <= :toInclusive
            group by d.id.aggDate
            order by d.id.aggDate
            """)
    List<Object[]> sumCostGroupedByDay(
            @Param("userId") String userId,
            @Param("apiKeyId") String apiKeyId,
            @Param("provider") AiProvider provider,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );
}
