package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.identity.events.ExternalApiKeyBudgetChangedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import com.zerobugfreinds.ai_agent_service.entity.IdentityApiKeySnapshotEntity;
import com.zerobugfreinds.ai_agent_service.repository.IdentityApiKeySnapshotRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class IdentityApiKeySnapshotService {

	private final IdentityApiKeySnapshotRepository snapshotRepository;

	public IdentityApiKeySnapshotService(IdentityApiKeySnapshotRepository snapshotRepository) {
		this.snapshotRepository = snapshotRepository;
	}

	public void upsertStatus(ExternalApiKeyStatusChangedEvent event) {
		IdentityApiKeySnapshotEntity current = snapshotRepository
				.findByUserIdAndKeyId(event.userId(), event.keyId())
				.orElse(null);
		BigDecimal budget = current != null ? current.getMonthlyBudgetUsd() : null;
		Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
		String keyHash = (event.keyHash() != null && !event.keyHash().isBlank())
				? event.keyHash()
				: (current != null ? current.getKeyHash() : null);

		snapshotRepository.save(
				new IdentityApiKeySnapshotEntity(
						event.userId(),
						event.keyId(),
						event.alias(),
						event.provider(),
						event.visibility(),
						event.status().name(),
						budget,
						keyHash,
						updatedAt
				)
		);
	}

	public void upsertBudget(ExternalApiKeyBudgetChangedEvent event) {
		IdentityApiKeySnapshotEntity current = snapshotRepository
				.findByUserIdAndKeyId(event.userId(), event.keyId())
				.orElse(null);
		Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

		String alias = event.alias();
		if ((alias == null || alias.isBlank()) && current != null) {
			alias = current.getAlias();
		}
		String provider = event.provider();
		if ((provider == null || provider.isBlank()) && current != null) {
			provider = current.getProvider();
		}
		String visibility = event.visibility();
		if ((visibility == null || visibility.isBlank()) && current != null) {
			visibility = current.getVisibility();
		}
		String status = event.status() != null ? event.status().name() : (current != null ? current.getStatus() : "ACTIVE");
		String keyHash = (event.keyHash() != null && !event.keyHash().isBlank())
				? event.keyHash()
				: (current != null ? current.getKeyHash() : null);

		snapshotRepository.save(
				new IdentityApiKeySnapshotEntity(
						event.userId(),
						event.keyId(),
						alias,
						provider,
						visibility,
						status,
						event.monthlyBudgetUsd(),
						keyHash,
						updatedAt
				)
		);
	}

	public void delete(ExternalApiKeyDeletedEvent event) {
		snapshotRepository.deleteByUserIdAndKeyId(event.userId(), event.apiKeyId());
	}

	public List<ApiKeySnapshot> findByUserId(Long userId) {
		return snapshotRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(this::toSnapshot)
				.toList();
	}

	public List<ApiKeySnapshot> findAll() {
		return snapshotRepository.findAllByOrderByUpdatedAtDesc().stream()
				.map(this::toSnapshot)
				.toList();
	}

	private ApiKeySnapshot toSnapshot(IdentityApiKeySnapshotEntity entity) {
		return new ApiKeySnapshot(
				entity.getKeyId(),
				entity.getUserId(),
				entity.getAlias(),
				entity.getProvider(),
				entity.getVisibility(),
				entity.getStatus(),
				entity.getMonthlyBudgetUsd(),
				entity.getKeyHash(),
				entity.getUpdatedAt()
		);
	}

	public record ApiKeySnapshot(
			Long keyId,
			Long userId,
			String alias,
			String provider,
			String visibility,
			String status,
			BigDecimal monthlyBudgetUsd,
			String keyHash,
			Instant updatedAt
	) {
	}
}
