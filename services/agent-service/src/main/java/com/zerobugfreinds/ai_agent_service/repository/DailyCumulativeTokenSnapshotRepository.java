package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.entity.DailyCumulativeTokenSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyCumulativeTokenSnapshotRepository extends JpaRepository<
		DailyCumulativeTokenSnapshotEntity,
		DailyCumulativeTokenSnapshotEntity.DailyCumulativeTokenSnapshotId> {

	Optional<DailyCumulativeTokenSnapshotEntity> findByTeamIdAndUserIdAndApiKeyId(
			String teamId,
			String userId,
			String apiKeyId
	);

	List<DailyCumulativeTokenSnapshotEntity> findAllByOrderByOccurredAtDesc();

	long deleteByApiKeyId(String apiKeyId);
}
