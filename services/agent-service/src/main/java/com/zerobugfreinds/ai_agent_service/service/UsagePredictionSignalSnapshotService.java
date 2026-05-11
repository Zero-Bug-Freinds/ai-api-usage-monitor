package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.UsagePredictionSignalsEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.entity.UsagePredictionSignalSnapshotEntity;
import com.zerobugfreinds.ai_agent_service.repository.UsagePredictionSignalSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class UsagePredictionSignalSnapshotService {

	private final UsagePredictionSignalSnapshotRepository snapshotRepository;
	private final ObjectMapper objectMapper;

	public UsagePredictionSignalSnapshotService(
			UsagePredictionSignalSnapshotRepository snapshotRepository,
			ObjectMapper objectMapper
	) {
		this.snapshotRepository = snapshotRepository;
		this.objectMapper = objectMapper;
	}

	public void upsert(UsagePredictionSignalsEvent event) {
		if (event == null || event.userId() == null || event.userId().isBlank()) {
			return;
		}
		String teamId = event.teamId() != null ? event.teamId().trim() : "";
		String userId = event.userId().trim();
		UsagePredictionSignalSnapshotEntity entity = snapshotRepository
				.findByTeamIdAndUserId(teamId, userId)
				.orElse(new UsagePredictionSignalSnapshotEntity(
						teamId,
						userId,
						null,
						null,
						"[]",
						null
				));
		entity.setAverageDailySpendUsd7d(event.averageDailySpendUsd7d());
		entity.setAverageDailyTokenUsage7d(event.averageDailyTokenUsage7d());
		entity.setRecentDailySpendUsdJson(serializeRecentDailySpend(event.recentDailySpendUsd()));
		entity.setPublishedAt(event.publishedAt());
		snapshotRepository.save(entity);
	}

	@Transactional(readOnly = true)
	public List<UsagePredictionSignalSnapshot> findAll() {
		return snapshotRepository.findAllByOrderByPublishedAtDesc().stream()
				.map(entity -> new UsagePredictionSignalSnapshot(
						entity.getTeamId(),
						entity.getUserId(),
						entity.getAverageDailySpendUsd7d(),
						entity.getAverageDailyTokenUsage7d(),
						deserializeRecentDailySpend(entity.getRecentDailySpendUsdJson()),
						entity.getPublishedAt()
				))
				.toList();
	}

	public record UsagePredictionSignalSnapshot(
			String teamId,
			String userId,
			BigDecimal averageDailySpendUsd7d,
			BigDecimal averageDailyTokenUsage7d,
			List<BigDecimal> recentDailySpendUsd,
			Instant publishedAt
	) {
	}

	private String serializeRecentDailySpend(List<BigDecimal> values) {
		try {
			return objectMapper.writeValueAsString(values == null ? List.<BigDecimal>of() : values);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("USAGE_PREDICTION_RECENT_SPEND_SERIALIZATION_FAILED", e);
		}
	}

	private List<BigDecimal> deserializeRecentDailySpend(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<BigDecimal>>() {});
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("USAGE_PREDICTION_RECENT_SPEND_DESERIALIZATION_FAILED", e);
		}
	}
}
