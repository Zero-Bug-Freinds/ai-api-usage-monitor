package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.dto.RecommendationScopeType;
import com.zerobugfreinds.ai_agent_service.entity.RecommendationSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationSnapshotRepository extends JpaRepository<RecommendationSnapshotEntity, Long> {

	Optional<RecommendationSnapshotEntity> findFirstByScopeTypeAndScopeIdAndKeyIdOrderByGeneratedAtDesc(
			RecommendationScopeType scopeType,
			String scopeId,
			String keyId
	);

	long deleteByKeyId(String keyId);
}
