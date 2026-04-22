package com.eevee.usageservice.repository;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UsageRecordedLogRepository extends JpaRepository<UsageRecordedLogEntity, UUID> {

    long countByApiKeyId(String apiKeyId);

    boolean existsByEventId(UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UsageRecordedLogEntity u set u.estimatedCost = :cost where u.eventId = :eventId")
    int updateEstimatedCostByEventId(@Param("eventId") UUID eventId, @Param("cost") BigDecimal cost);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UsageRecordedLogEntity u where u.apiKeyId = :apiKeyId")
    int deleteByApiKeyId(@Param("apiKeyId") String apiKeyId);

    @Query(
            value = """
                    select u from UsageRecordedLogEntity u
                    left join fetch u.apiKeyMetadata m
                    where u.userId = :userId
                    and u.occurredAt >= :from
                    and u.occurredAt < :toExclusive
                    and (:provider is null or u.provider = :provider)
                    and (:apiKeyId is null or :apiKeyId = '' or u.apiKeyId = :apiKeyId)
                    and (:requestSuccessful is null or u.requestSuccessful = :requestSuccessful)
                    and (:modelMask is null or :modelMask = '' or lower(coalesce(u.model, '')) like lower(concat('%', :modelMask, '%')))
                    and (
                        :reasoningPresence is null or :reasoningPresence = ''
                        or (:reasoningPresence = 'present' and u.estimatedReasoningTokens is not null and u.estimatedReasoningTokens > 0)
                        or (:reasoningPresence = 'absent' and (u.estimatedReasoningTokens is null or u.estimatedReasoningTokens <= 0))
                    )
                    order by u.occurredAt desc
                    """,
            countQuery = """
                    select count(u) from UsageRecordedLogEntity u
                    where u.userId = :userId
                    and u.occurredAt >= :from
                    and u.occurredAt < :toExclusive
                    and (:provider is null or u.provider = :provider)
                    and (:apiKeyId is null or :apiKeyId = '' or u.apiKeyId = :apiKeyId)
                    and (:requestSuccessful is null or u.requestSuccessful = :requestSuccessful)
                    and (:modelMask is null or :modelMask = '' or lower(coalesce(u.model, '')) like lower(concat('%', :modelMask, '%')))
                    and (
                        :reasoningPresence is null or :reasoningPresence = ''
                        or (:reasoningPresence = 'present' and u.estimatedReasoningTokens is not null and u.estimatedReasoningTokens > 0)
                        or (:reasoningPresence = 'absent' and (u.estimatedReasoningTokens is null or u.estimatedReasoningTokens <= 0))
                    )
                    """
    )
    Page<UsageRecordedLogEntity> pageLogs(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("provider") AiProvider provider,
            @Param("apiKeyId") String apiKeyId,
            @Param("requestSuccessful") Boolean requestSuccessful,
            @Param("modelMask") String modelMask,
            @Param("reasoningPresence") String reasoningPresence,
            Pageable pageable
    );

    @Query(
            """
                    select new com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse(
                        u.apiKeyId,
                        m.alias,
                        m.status
                    )
                    from UsageRecordedLogEntity u
                    left join u.apiKeyMetadata m
                    where u.userId = :userId
                    and u.occurredAt >= :from
                    and u.occurredAt < :toExclusive
                    and u.apiKeyId is not null
                    and trim(u.apiKeyId) <> ''
                    and (:provider is null or u.provider = :provider)
                    group by u.apiKeyId, m.alias, m.status
                    order by u.apiKeyId
                    """
    )
    List<UsageLogApiKeyItemResponse> findDistinctApiKeysForUserInRange(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("provider") AiProvider provider
    );
}