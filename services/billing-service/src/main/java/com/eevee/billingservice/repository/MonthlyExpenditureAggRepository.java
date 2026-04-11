package com.eevee.billingservice.repository;

import com.eevee.billingservice.domain.MonthlyExpenditureAggEntity;
import com.eevee.billingservice.domain.MonthlyExpenditureAggId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface MonthlyExpenditureAggRepository extends JpaRepository<MonthlyExpenditureAggEntity, MonthlyExpenditureAggId> {

    @Query("""
            select m from MonthlyExpenditureAggEntity m
            where m.id.userId = :userId and m.id.apiKeyId = :apiKeyId
            and m.id.monthStartDate >= :fromInclusive and m.id.monthStartDate <= :toInclusive
            order by m.id.monthStartDate
            """)
    List<MonthlyExpenditureAggEntity> findSeries(
            @Param("userId") String userId,
            @Param("apiKeyId") String apiKeyId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MonthlyExpenditureAggEntity m
            set m.finalized = true, m.finalizedAt = :finalizedAt
            where m.id.monthStartDate = :monthStart
            and m.finalized = false
            """)
    int finalizeMonth(@Param("monthStart") LocalDate monthStart, @Param("finalizedAt") Instant finalizedAt);
}
