package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.entity.BillingSignalSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingSignalSnapshotRepository extends JpaRepository<BillingSignalSnapshotEntity, String> {

	Optional<BillingSignalSnapshotEntity> findByApiKeyId(String apiKeyId);

	List<BillingSignalSnapshotEntity> findAllByOrderByLatestFinalizedAtDesc();

	List<BillingSignalSnapshotEntity> findByTeamIdOrderByLatestFinalizedAtDesc(String teamId);
}
