package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.DailyCumulativeTokensUpdatedEvent;
import com.zerobugfreinds.ai_agent_service.entity.DailyCumulativeTokenSnapshotEntity;
import com.zerobugfreinds.ai_agent_service.repository.DailyCumulativeTokenSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DailyCumulativeTokenSnapshotService {

	private final DailyCumulativeTokenSnapshotRepository snapshotRepository;

	public DailyCumulativeTokenSnapshotService(DailyCumulativeTokenSnapshotRepository snapshotRepository) {
		this.snapshotRepository = snapshotRepository;
	}

	public void upsert(DailyCumulativeTokensUpdatedEvent event) {
		if (event == null || event.userId() == null || event.userId().isBlank()) {
			return;
		}

		String teamId = normalize(event.teamId());
		String userId = normalize(event.userId());
		String apiKeyId = normalize(event.apiKeyId());
		DailyCumulativeTokenSnapshotEntity entity = snapshotRepository
				.findByTeamIdAndUserIdAndApiKeyId(teamId, userId, apiKeyId)
				.orElse(new DailyCumulativeTokenSnapshotEntity(teamId, userId, apiKeyId, 0L, null));
		entity.setDailyTotalTokens(event.dailyTotalTokens());
		entity.setOccurredAt(event.occurredAt() != null ? event.occurredAt() : Instant.now());
		snapshotRepository.save(entity);
	}

	public List<DailyCumulativeTokenSnapshot> findAll() {
		return snapshotRepository.findAllByOrderByOccurredAtDesc().stream()
				.map(entity -> new DailyCumulativeTokenSnapshot(
						entity.getTeamId(),
						entity.getUserId(),
						entity.getApiKeyId(),
						entity.getDailyTotalTokens(),
						entity.getOccurredAt()
				))
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

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
