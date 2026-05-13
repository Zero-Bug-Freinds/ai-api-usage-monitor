package com.zerobugfreinds.ai_agent_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "usage_prediction_signal_projection")
@IdClass(UsagePredictionSignalSnapshotEntity.UsagePredictionSignalSnapshotId.class)
public class UsagePredictionSignalSnapshotEntity {

	@Id
	@Column(nullable = false, length = 120)
	private String teamId;

	@Id
	@Column(nullable = false, length = 120)
	private String userId;

	@Column(precision = 19, scale = 4)
	private BigDecimal averageDailySpendUsd7d;

	@Column(precision = 19, scale = 4)
	private BigDecimal averageDailyTokenUsage7d;

	@Lob
	@Column(nullable = false)
	private String recentDailySpendUsdJson;

	private Instant publishedAt;

	protected UsagePredictionSignalSnapshotEntity() {
	}

	public UsagePredictionSignalSnapshotEntity(
			String teamId,
			String userId,
			BigDecimal averageDailySpendUsd7d,
			BigDecimal averageDailyTokenUsage7d,
			String recentDailySpendUsdJson,
			Instant publishedAt
	) {
		this.teamId = teamId;
		this.userId = userId;
		this.averageDailySpendUsd7d = averageDailySpendUsd7d;
		this.averageDailyTokenUsage7d = averageDailyTokenUsage7d;
		this.recentDailySpendUsdJson = recentDailySpendUsdJson;
		this.publishedAt = publishedAt;
	}

	public String getTeamId() { return teamId; }
	public String getUserId() { return userId; }
	public BigDecimal getAverageDailySpendUsd7d() { return averageDailySpendUsd7d; }
	public BigDecimal getAverageDailyTokenUsage7d() { return averageDailyTokenUsage7d; }
	public String getRecentDailySpendUsdJson() { return recentDailySpendUsdJson; }
	public Instant getPublishedAt() { return publishedAt; }

	public void setAverageDailySpendUsd7d(BigDecimal averageDailySpendUsd7d) { this.averageDailySpendUsd7d = averageDailySpendUsd7d; }
	public void setAverageDailyTokenUsage7d(BigDecimal averageDailyTokenUsage7d) { this.averageDailyTokenUsage7d = averageDailyTokenUsage7d; }
	public void setRecentDailySpendUsdJson(String recentDailySpendUsdJson) { this.recentDailySpendUsdJson = recentDailySpendUsdJson; }
	public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

	public static class UsagePredictionSignalSnapshotId implements Serializable {
		private String teamId;
		private String userId;

		public UsagePredictionSignalSnapshotId() {
		}

		public UsagePredictionSignalSnapshotId(String teamId, String userId) {
			this.teamId = teamId;
			this.userId = userId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof UsagePredictionSignalSnapshotId that)) return false;
			return Objects.equals(teamId, that.teamId) && Objects.equals(userId, that.userId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(teamId, userId);
		}
	}
}
