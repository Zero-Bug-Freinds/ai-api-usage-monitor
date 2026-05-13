package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.entity.IdentityApiKeySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityApiKeySnapshotRepository
		extends JpaRepository<IdentityApiKeySnapshotEntity, IdentityApiKeySnapshotEntity.IdentityApiKeySnapshotId> {

	Optional<IdentityApiKeySnapshotEntity> findByUserIdAndKeyId(String userId, Long keyId);

	List<IdentityApiKeySnapshotEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

	List<IdentityApiKeySnapshotEntity> findAllByOrderByUpdatedAtDesc();

	void deleteByUserIdAndKeyId(String userId, Long keyId);
}
