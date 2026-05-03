package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.UsagePredictionSignalsEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UsagePredictionSignalSnapshotService {

	private final Map<String, UsagePredictionSignalSnapshot> byScope = new ConcurrentHashMap<>();

	public void upsert(UsagePredictionSignalsEvent event) {
		if (event == null || event.userId() == null || event.userId().isBlank()) {
			return;
		}
		String teamId = event.teamId() != null ? event.teamId().trim() : "";
		String userId = event.userId().trim();
		String key = scopeKey(teamId, userId);
		byScope.put(
				key,
				new UsagePredictionSignalSnapshot(
						teamId,
						userId,
						event.averageDailySpendUsd7d(),
						event.averageDailyTokenUsage7d(),
						event.recentDailySpendUsd(),
						event.publishedAt()
				)
		);
	}

	public List<UsagePredictionSignalSnapshot> findAll() {
		return byScope.values().stream()
				.sorted(Comparator.comparing(UsagePredictionSignalSnapshot::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	public record UsagePredictionSignalSnapshot(
			String teamId,
			String userId,
			BigDecimal averageDailySpendUsd7d,
			BigDecimal averageDailyTokenUsage7d,
			List<BigDecimal> recentDailySpendUsd,
			Instant publishedAt
	) {
	}

	private static String scopeKey(String teamId, String userId) {
		return (teamId == null ? "" : teamId) + "|" + userId;
	}
}
