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

	public void delete(ExternalApiKeyDeletedEvent event) {
		Map<Long, ApiKeySnapshot> userKeys = snapshotsByUserId.get(event.userId());
		if (userKeys == null) {
			return;
		}
		userKeys.remove(event.apiKeyId());
		if (userKeys.isEmpty()) {
			snapshotsByUserId.remove(event.userId());
		}
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
