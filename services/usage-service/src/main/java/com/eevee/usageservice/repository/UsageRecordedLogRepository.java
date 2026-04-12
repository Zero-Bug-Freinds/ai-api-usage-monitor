package com.eevee.usageservice.repository;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface UsageRecordedLogRepository extends JpaRepository<UsageRecordedLogEntity, UUID> {

    boolean existsByEventId(UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UsageRecordedLogEntity u set u.estimatedCost = :cost where u.eventId = :eventId")
    int updateEstimatedCostByEventId(@Param("eventId") UUID eventId, @Param("cost") BigDecimal cost);

    @Query(
            value = """
                    select u from UsageRecordedLogEntity u
                    where u.userId = :userId
                    and u.occurredAt >= :from
                    and u.occurredAt < :toExclusive
                    and (:provider is null or u.provider = :provider)
                    and (:modelMask is null or :modelMask = '' or lower(coalesce(u.model, '')) like lower(concat('%', :modelMask, '%')))
                    order by u.occurredAt desc
                    """,
            countQuery = """
                    select count(u) from UsageRecordedLogEntity u
                    where u.userId = :userId
                    and u.occurredAt >= :from
                    and u.occurredAt < :toExclusive
                    and (:provider is null or u.provider = :provider)
                    and (:modelMask is null or :modelMask = '' or lower(coalesce(u.model, '')) like lower(concat('%', :modelMask, '%')))
                    """
    )
    Page<UsageRecordedLogEntity> pageLogs(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("provider") AiProvider provider,
            @Param("modelMask") String modelMask,
            Pageable pageable
    );
}