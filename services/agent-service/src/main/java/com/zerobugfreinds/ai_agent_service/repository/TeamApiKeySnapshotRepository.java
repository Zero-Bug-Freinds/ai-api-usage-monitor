package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.entity.TeamApiKeySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamApiKeySnapshotRepository
		extends JpaRepository<TeamApiKeySnapshotEntity, TeamApiKeySnapshotEntity.TeamApiKeySnapshotId> {

	Optional<TeamApiKeySnapshotEntity> findByTeamIdAndTeamApiKeyId(Long teamId, Long teamApiKeyId);

	List<TeamApiKeySnapshotEntity> findByTeamIdOrderByUpdatedAtDesc(Long teamId);

	List<TeamApiKeySnapshotEntity> findAllByOrderByUpdatedAtDesc();
}
