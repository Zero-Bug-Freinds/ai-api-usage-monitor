package com.zerobugfreinds.ai_agent_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "daily_cumulative_token_projection")
@IdClass(DailyCumulativeTokenSnapshotEntity.DailyCumulativeTokenSnapshotId.class)
public class DailyCumulativeTokenSnapshotEntity {

	@Id
	@Column(nullable = false, length = 120)
	private String teamId;

	@Id
	@Column(nullable = false, length = 120)
	private String userId;

	@Id
	@Column(nullable = false, length = 120)
	private String apiKeyId;

	@Column(nullable = false)
	private long dailyTotalTokens;

	private Instant occurredAt;

	protected DailyCumulativeTokenSnapshotEntity() {
	}

	public DailyCumulativeTokenSnapshotEntity(
			String teamId,
			String userId,
			String apiKeyId,
			long dailyTotalTokens,
			Instant occurredAt
	) {
		this.teamId = teamId;
		this.userId = userId;
		this.apiKeyId = apiKeyId;
		this.dailyTotalTokens = dailyTotalTokens;
		this.occurredAt = occurredAt;
	}

	public String getTeamId() { return teamId; }
	public String getUserId() { return userId; }
	public String getApiKeyId() { return apiKeyId; }
	public long getDailyTotalTokens() { return dailyTotalTokens; }
	public Instant getOccurredAt() { return occurredAt; }

	public void setDailyTotalTokens(long dailyTotalTokens) { this.dailyTotalTokens = dailyTotalTokens; }
	public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

	public static class DailyCumulativeTokenSnapshotId implements Serializable {
		private String teamId;
		private String userId;
		private String apiKeyId;

		public DailyCumulativeTokenSnapshotId() {
		}

		public DailyCumulativeTokenSnapshotId(String teamId, String userId, String apiKeyId) {
			this.teamId = teamId;
			this.userId = userId;
			this.apiKeyId = apiKeyId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof DailyCumulativeTokenSnapshotId that)) return false;
			return Objects.equals(teamId, that.teamId)
					&& Objects.equals(userId, that.userId)
					&& Objects.equals(apiKeyId, that.apiKeyId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(teamId, userId, apiKeyId);
		}
	}
}
