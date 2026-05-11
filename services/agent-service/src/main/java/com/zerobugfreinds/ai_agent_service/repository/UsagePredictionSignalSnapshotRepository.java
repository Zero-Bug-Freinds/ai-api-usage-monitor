package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.entity.UsagePredictionSignalSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsagePredictionSignalSnapshotRepository extends JpaRepository<
		UsagePredictionSignalSnapshotEntity,
		UsagePredictionSignalSnapshotEntity.UsagePredictionSignalSnapshotId> {

	Optional<UsagePredictionSignalSnapshotEntity> findByTeamIdAndUserId(String teamId, String userId);

	List<UsagePredictionSignalSnapshotEntity> findAllByOrderByPublishedAtDesc();
}
