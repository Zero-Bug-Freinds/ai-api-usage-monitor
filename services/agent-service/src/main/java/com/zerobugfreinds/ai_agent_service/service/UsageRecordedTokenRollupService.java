package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.entity.UsageRecordedTokenRollupEntity;
import com.zerobugfreinds.ai_agent_service.repository.UsageRecordedTokenRollupRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class UsageRecordedTokenRollupService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UsageRecordedTokenRollupRepository rollupRepository;

	public UsageRecordedTokenRollupService(UsageRecordedTokenRollupRepository rollupRepository) {
		this.rollupRepository = rollupRepository;
	}

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
		String normalizedKeyId = keyId.trim();
		String normalizedScopeType = normalize(scopeType);
		String normalizedScopeId = normalize(scopeId);
		long sanitizedLatency = sanitize(latencyMs);

		UsageRecordedTokenRollupEntity entity = rollupRepository
				.findByKeyIdAndScopeTypeAndScopeIdAndDay(normalizedKeyId, normalizedScopeType, normalizedScopeId, day)
				.orElse(new UsageRecordedTokenRollupEntity(
						normalizedKeyId,
						normalizedScopeType,
						normalizedScopeId,
						day,
						0L,
						0L,
						0L,
						0L,
						occurredAt
				));
		entity.setInputTokens(entity.getInputTokens() + input);
		entity.setOutputTokens(entity.getOutputTokens() + output);
		entity.setRequestCount(entity.getRequestCount() + 1);
		entity.setTotalLatencyMs(entity.getTotalLatencyMs() + sanitizedLatency);
		entity.setLastUpdatedAt(
				occurredAt.isAfter(entity.getLastUpdatedAt()) ? occurredAt : entity.getLastUpdatedAt()
		);
		rollupRepository.save(entity);
	}

	public SevenDayTokenSummary summarizeLastSevenDays(String keyId, String scopeType, String scopeId) {
		if (keyId == null || keyId.isBlank()) {
			return new SevenDayTokenSummary(0L, 0L, 0L, null);
		}
		LocalDate today = LocalDate.now(KST);
		LocalDate from = today.minusDays(6);
		String normalizedKeyId = keyId.trim();
		String normalizedScopeType = normalize(scopeType);
		String normalizedScopeId = normalize(scopeId);

		long totalInput = 0L;
		long totalOutput = 0L;
		long totalRequests = 0L;
		long totalLatencyMs = 0L;
		List<UsageRecordedTokenRollupEntity> rows = rollupRepository.findByKeyIdAndScopeTypeAndScopeIdAndDayBetween(
				normalizedKeyId,
				normalizedScopeType,
				normalizedScopeId,
				from,
				today
		);
		for (UsageRecordedTokenRollupEntity row : rows) {
			totalInput += row.getInputTokens();
			totalOutput += row.getOutputTokens();
			totalRequests += row.getRequestCount();
			totalLatencyMs += row.getTotalLatencyMs();
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

	public record SevenDayTokenSummary(
			long totalInputTokens,
			long totalOutputTokens,
			long totalRequests,
			Long averageLatencyMs
	) {
	}
}
