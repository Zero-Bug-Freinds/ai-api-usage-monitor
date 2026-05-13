package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.entity.TeamApiKeySnapshotEntity;
import com.zerobugfreinds.ai_agent_service.repository.TeamApiKeySnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TeamApiKeySnapshotService {

	private final TeamApiKeySnapshotRepository snapshotRepository;

	public TeamApiKeySnapshotService(TeamApiKeySnapshotRepository snapshotRepository) {
		this.snapshotRepository = snapshotRepository;
	}

	public void upsert(TeamApiKeySnapshot snapshot) {
		TeamApiKeySnapshotEntity entity = snapshotRepository
				.findByTeamIdAndTeamApiKeyId(snapshot.teamId(), snapshot.teamApiKeyId())
				.orElse(
						new TeamApiKeySnapshotEntity(
								snapshot.teamId(),
								snapshot.teamApiKeyId(),
								snapshot.teamName(),
								snapshot.ownerUserId(),
								snapshot.visibility(),
								snapshot.alias(),
								snapshot.provider(),
								snapshot.status(),
								snapshot.retainLogs(),
								snapshot.keyHash(),
								snapshot.updatedAt()
						)
				);
		entity.setTeamName(snapshot.teamName());
		entity.setOwnerUserId(snapshot.ownerUserId());
		entity.setVisibility(snapshot.visibility());
		entity.setAlias(snapshot.alias());
		entity.setProvider(snapshot.provider());
		entity.setStatus(snapshot.status());
		entity.setRetainLogs(snapshot.retainLogs());
		entity.setKeyHash(snapshot.keyHash());
		entity.setUpdatedAt(snapshot.updatedAt() != null ? snapshot.updatedAt() : Instant.now());
		snapshotRepository.save(entity);
	}

	public List<TeamApiKeySnapshot> findByTeamId(Long teamId) {
		return snapshotRepository.findByTeamIdOrderByUpdatedAtDesc(teamId).stream()
				.map(this::toSnapshot)
				.toList();
	}

	public List<TeamApiKeySnapshot> findAll() {
		return snapshotRepository.findAllByOrderByUpdatedAtDesc().stream()
				.map(this::toSnapshot)
				.toList();
	}

	private TeamApiKeySnapshot toSnapshot(TeamApiKeySnapshotEntity entity) {
		return new TeamApiKeySnapshot(
				entity.getTeamId(),
				entity.getTeamName(),
				entity.getTeamApiKeyId(),
				entity.getOwnerUserId(),
				entity.getVisibility(),
				entity.getAlias(),
				entity.getProvider(),
				entity.getStatus(),
				entity.getRetainLogs(),
				entity.getKeyHash(),
				entity.getUpdatedAt()
		);
	}

	public record TeamApiKeySnapshot(
			Long teamId,
			String teamName,
			Long teamApiKeyId,
			String ownerUserId,
			String visibility,
			String alias,
			String provider,
			String status,
			Boolean retainLogs,
			String keyHash,
			Instant updatedAt
	) {
	}
}
