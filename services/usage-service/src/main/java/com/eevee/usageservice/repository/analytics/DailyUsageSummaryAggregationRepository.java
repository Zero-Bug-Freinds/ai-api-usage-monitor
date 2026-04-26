package com.eevee.usageservice.repository.analytics;

import com.eevee.usageservice.mq.UsageSummaryAggregationMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.UUID;

@Repository
public class DailyUsageSummaryAggregationRepository {

    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;

    public DailyUsageSummaryAggregationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean registerProcessedEvent(UUID eventId) {
        String sql = """
                INSERT INTO processed_summary_event(event_id, processed_at)
                VALUES (?, now())
                ON CONFLICT (event_id) DO NOTHING
                """;
        Integer updated = jdbcTemplate.update(sql, eventId);
        return updated != null && updated > 0;
    }

    public int upsertDailySummary(UsageSummaryAggregationMessage message) {
        String sql = """
                INSERT INTO daily_usage_summary(
                    usage_date, team_id, user_id, model, provider,
                    request_count, success_count, error_count,
                    total_tokens, prompt_tokens, completion_tokens, reasoning_tokens,
                    total_cost, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (usage_date, team_id, user_id, model, provider)
                DO UPDATE SET
                    request_count = daily_usage_summary.request_count + EXCLUDED.request_count,
                    success_count = daily_usage_summary.success_count + EXCLUDED.success_count,
                    error_count = daily_usage_summary.error_count + EXCLUDED.error_count,
                    total_tokens = daily_usage_summary.total_tokens + EXCLUDED.total_tokens,
                    prompt_tokens = daily_usage_summary.prompt_tokens + EXCLUDED.prompt_tokens,
                    completion_tokens = daily_usage_summary.completion_tokens + EXCLUDED.completion_tokens,
                    reasoning_tokens = daily_usage_summary.reasoning_tokens + EXCLUDED.reasoning_tokens,
                    total_cost = daily_usage_summary.total_cost + EXCLUDED.total_cost,
                    updated_at = now()
                """;
        return jdbcTemplate.update(
                sql,
                Date.valueOf(message.occurredAt().atZone(DASHBOARD_ZONE).toLocalDate()),
                normalizeTeamId(message.teamId()),
                message.userId(),
                message.model(),
                message.provider(),
                message.requestCount(),
                message.successCount(),
                message.errorCount(),
                message.totalTokens(),
                message.promptTokens(),
                message.completionTokens(),
                message.reasoningTokens(),
                defaultCost(message.totalCost())
        );
    }

    public int upsertBackfillRow(
            java.time.LocalDate usageDate,
            String teamId,
            String userId,
            String provider,
            String model,
            long requestCount,
            long successCount,
            long errorCount,
            long totalTokens,
            long promptTokens,
            long completionTokens,
            long reasoningTokens,
            BigDecimal totalCost
    ) {
        UsageSummaryAggregationMessage message = new UsageSummaryAggregationMessage(
                UUID.randomUUID(),
                Timestamp.valueOf(usageDate.atStartOfDay()).toInstant(),
                teamId,
                userId,
                provider,
                model,
                requestCount,
                successCount,
                errorCount,
                totalTokens,
                promptTokens,
                completionTokens,
                reasoningTokens,
                totalCost
        );
        return upsertDailySummary(message);
    }

    private static BigDecimal defaultCost(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String normalizeTeamId(String teamId) {
        return teamId != null ? teamId : "";
    }
}
