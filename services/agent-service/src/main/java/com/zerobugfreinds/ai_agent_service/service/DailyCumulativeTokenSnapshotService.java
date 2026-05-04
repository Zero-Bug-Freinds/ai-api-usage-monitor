package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.DailyCumulativeTokensUpdatedEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DailyCumulativeTokenSnapshotService {

	private final Map<String, DailyCumulativeTokenSnapshot> byScope = new ConcurrentHashMap<>();

	public void upsert(DailyCumulativeTokensUpdatedEvent event) {
		if (event == null || event.userId() == null || event.userId().isBlank()) {
			return;
		}

		String teamId = normalize(event.teamId());
		String userId = normalize(event.userId());
		String apiKeyId = normalize(event.apiKeyId());
		String scopeKey = scopeKey(teamId, userId, apiKeyId);

		byScope.put(
				scopeKey,
				new DailyCumulativeTokenSnapshot(
						teamId,
						userId,
						apiKeyId,
						event.dailyTotalTokens(),
						event.occurredAt()
				)
		);
	}

	public List<DailyCumulativeTokenSnapshot> findAll() {
		return byScope.values().stream()
				.sorted(Comparator.comparing(DailyCumulativeTokenSnapshot::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	public record DailyCumulativeTokenSnapshot(
			String teamId,
			String userId,
			String apiKeyId,
			long dailyTotalTokens,
			Instant occurredAt
	) {
	}

	private static String scopeKey(String teamId, String userId, String apiKeyId) {
		return teamId + "|" + userId + "|" + apiKeyId;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
