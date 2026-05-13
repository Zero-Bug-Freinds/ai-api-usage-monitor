package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.identity.events.ExternalApiKeyBudgetChangedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdentityApiKeySnapshotService {

	private final Map<Long, Map<Long, ApiKeySnapshot>> snapshotsByUserId = new ConcurrentHashMap<>();

	public void upsertStatus(ExternalApiKeyStatusChangedEvent event) {
		Map<Long, ApiKeySnapshot> userKeys = snapshotsByUserId.computeIfAbsent(
				event.userId(),
				unused -> new ConcurrentHashMap<>()
		);
		ApiKeySnapshot current = userKeys.get(event.keyId());
		BigDecimal budget = current != null ? current.monthlyBudgetUsd() : null;
		Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

		userKeys.put(
				event.keyId(),
				new ApiKeySnapshot(
						event.keyId(),
						event.userId(),
						event.alias(),
						event.provider(),
						event.visibility(),
						event.status().name(),
						budget,
						updatedAt
				)
		);
	}

	public void upsertBudget(ExternalApiKeyBudgetChangedEvent event) {
		Map<Long, ApiKeySnapshot> userKeys = snapshotsByUserId.computeIfAbsent(
				event.userId(),
				unused -> new ConcurrentHashMap<>()
		);
		ApiKeySnapshot current = userKeys.get(event.keyId());
		Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

		String alias = event.alias();
		if ((alias == null || alias.isBlank()) && current != null) {
			alias = current.alias();
		}
		String provider = event.provider();
		if ((provider == null || provider.isBlank()) && current != null) {
			provider = current.provider();
		}
		String visibility = event.visibility();
		if ((visibility == null || visibility.isBlank()) && current != null) {
			visibility = current.visibility();
		}
		String status = event.status() != null ? event.status().name() : (current != null ? current.status() : "ACTIVE");

		userKeys.put(
				event.keyId(),
				new ApiKeySnapshot(
						event.keyId(),
						event.userId(),
						alias,
						provider,
						visibility,
						status,
						event.monthlyBudgetUsd(),
						updatedAt
				)
		);
	}

<<<<<<< Updated upstream
	public void delete(ExternalApiKeyDeletedEvent event) {
		Map<Long, ApiKeySnapshot> userKeys = snapshotsByUserId.get(event.userId());
		if (userKeys == null) {
			return;
		}
		userKeys.remove(event.apiKeyId());
		if (userKeys.isEmpty()) {
			snapshotsByUserId.remove(event.userId());
		}
=======
	/**
	 * 물리 삭제 이벤트: {@code retainLogs=false} 면 projection 행 제거(usage 로그·집계 purge 는 리스너).
	 * {@code retainLogs=true} 면 usage-service {@code ApiKeyMetadataSyncService} 와 같이 행을 유지하고
	 * 상태만 {@code DELETED} 로 남겨 대시보드·비서 UI에서 “삭제됐지만 로그 보존” 키를 계속 조회할 수 있게 한다.
	 */
	public void handleExternalApiKeyDeleted(ExternalApiKeyDeletedEvent event) {
		if (event.userId() == null || event.userId().isBlank() || event.apiKeyId() == null) {
			throw new IllegalArgumentException("userId and apiKeyId are required");
		}
		if (!event.retainLogs()) {
			snapshotRepository.deleteByUserIdAndKeyId(event.userId(), event.apiKeyId());
			return;
		}
		Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
		IdentityApiKeySnapshotEntity current = snapshotRepository
				.findByUserIdAndKeyId(event.userId(), event.apiKeyId())
				.orElse(null);

		String alias = textOrElse(event.alias(), current != null ? current.getAlias() : null);
		String provider = textOrElse(event.provider(), current != null ? current.getProvider() : null);
		String visibility = current != null ? current.getVisibility() : null;
		BigDecimal budget = current != null ? current.getMonthlyBudgetUsd() : null;
		String keyHash = current != null ? current.getKeyHash() : null;

		snapshotRepository.save(
				new IdentityApiKeySnapshotEntity(
						event.userId(),
						event.apiKeyId(),
						alias,
						provider,
						visibility,
						"DELETED",
						budget,
						keyHash,
						updatedAt
				)
		);
	}

	private static String textOrElse(String preferred, String fallback) {
		if (preferred != null && !preferred.isBlank()) {
			return preferred.trim();
		}
		return fallback != null ? fallback : "";
>>>>>>> Stashed changes
	}

	public List<ApiKeySnapshot> findByUserId(Long userId) {
		Map<Long, ApiKeySnapshot> userKeys = snapshotsByUserId.get(userId);
		if (userKeys == null) {
			return List.of();
		}
		return userKeys.values().stream()
				.sorted(Comparator.comparing(ApiKeySnapshot::updatedAt).reversed())
				.toList();
	}

	public List<ApiKeySnapshot> findAll() {
		return snapshotsByUserId.values().stream()
				.flatMap(keys -> keys.values().stream())
				.sorted(Comparator.comparing(ApiKeySnapshot::updatedAt).reversed())
				.toList();
	}

	public record ApiKeySnapshot(
			Long keyId,
			Long userId,
			String alias,
			String provider,
			String visibility,
			String status,
			BigDecimal monthlyBudgetUsd,
			Instant updatedAt
	) {
	}
}
