package com.zerobugfreinds.ai_agent_service.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UsageRecordedTokenRollupService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final Map<RollupKey, DailyTokenRollup> rollups = new ConcurrentHashMap<>();

	public void add(
			String keyId,
			String scopeType,
			String scopeId,
			Instant occurredAt,
			Long promptTokens,
			Long completionTokens,
			Long latencyMs
	) {
		if (keyId == null || keyId.isBlank() || occurredAt == null) {
			return;
		}
		long input = sanitize(promptTokens);
		long output = sanitize(completionTokens);
		if (input <= 0 && output <= 0) {
			return;
		}
		LocalDate day = occurredAt.atZone(KST).toLocalDate();
		RollupKey key = new RollupKey(keyId.trim(), normalize(scopeType), normalize(scopeId), day);
		rollups.compute(key, (unused, current) -> {
			long sanitizedLatency = sanitize(latencyMs);
			if (current == null) {
				return new DailyTokenRollup(input, output, 1L, sanitizedLatency, occurredAt);
			}
			return new DailyTokenRollup(
					current.inputTokens() + input,
					current.outputTokens() + output,
					current.requestCount() + 1,
					current.totalLatencyMs() + sanitizedLatency,
					occurredAt.isAfter(current.lastUpdatedAt()) ? occurredAt : current.lastUpdatedAt()
			);
		});
	}

	public SevenDayTokenSummary summarizeLastSevenDays(String keyId, String scopeType, String scopeId) {
		if (keyId == null || keyId.isBlank()) {
			return new SevenDayTokenSummary(0L, 0L, 0L, null);
		}
		LocalDate today = LocalDate.now(KST);
		LocalDate from = today.minusDays(6);
		String normalizedScopeType = normalize(scopeType);
		String normalizedScopeId = normalize(scopeId);

		long totalInput = 0L;
		long totalOutput = 0L;
		long totalRequests = 0L;
		long totalLatencyMs = 0L;
		for (Map.Entry<RollupKey, DailyTokenRollup> entry : rollups.entrySet()) {
			RollupKey key = entry.getKey();
			if (!key.keyId().equals(keyId.trim())) {
				continue;
			}
			if (!key.scopeType().equals(normalizedScopeType)) {
				continue;
			}
			if (!key.scopeId().equals(normalizedScopeId)) {
				continue;
			}
			if (key.day().isBefore(from) || key.day().isAfter(today)) {
				continue;
			}
			DailyTokenRollup rollup = entry.getValue();
			totalInput += rollup.inputTokens();
			totalOutput += rollup.outputTokens();
			totalRequests += rollup.requestCount();
			totalLatencyMs += rollup.totalLatencyMs();
		}
		Long averageLatencyMs = totalRequests > 0 ? Math.round((double) totalLatencyMs / totalRequests) : null;
		return new SevenDayTokenSummary(totalInput, totalOutput, totalRequests, averageLatencyMs);
	}

	private static long sanitize(Long value) {
		if (value == null || value < 0) {
			return 0L;
		}
		return value;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private record RollupKey(
			String keyId,
			String scopeType,
			String scopeId,
			LocalDate day
	) {
	}

	private record DailyTokenRollup(
			long inputTokens,
			long outputTokens,
			long requestCount,
			long totalLatencyMs,
			Instant lastUpdatedAt
	) {
	}

	public record SevenDayTokenSummary(
			long totalInputTokens,
			long totalOutputTokens,
			long totalRequests,
			Long averageLatencyMs
	) {
	}
}
