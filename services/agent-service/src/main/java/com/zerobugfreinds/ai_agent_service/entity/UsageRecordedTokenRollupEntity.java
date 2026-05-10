package com.zerobugfreinds.ai_agent_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "usage_recorded_token_rollup")
@IdClass(UsageRecordedTokenRollupEntity.UsageRecordedTokenRollupId.class)
public class UsageRecordedTokenRollupEntity {

	@Id
	@Column(nullable = false, length = 120)
	private String keyId;

	@Id
	@Column(nullable = false, length = 20)
	private String scopeType;

	@Id
	@Column(nullable = false, length = 120)
	private String scopeId;

	@Id
	@Column(nullable = false)
	private LocalDate day;

	@Column(nullable = false)
	private long inputTokens;

	@Column(nullable = false)
	private long outputTokens;

	@Column(nullable = false)
	private long reasoningTokens;

	@Column(nullable = false)
	private long requestCount;

	@Column(nullable = false)
	private long totalLatencyMs;

	@Column(nullable = false)
	private Instant lastUpdatedAt;

	protected UsageRecordedTokenRollupEntity() {
	}

	public UsageRecordedTokenRollupEntity(
			String keyId,
			String scopeType,
			String scopeId,
			LocalDate day,
			long inputTokens,
			long outputTokens,
			long reasoningTokens,
			long requestCount,
			long totalLatencyMs,
			Instant lastUpdatedAt
	) {
		this.keyId = keyId;
		this.scopeType = scopeType;
		this.scopeId = scopeId;
		this.day = day;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.reasoningTokens = reasoningTokens;
		this.requestCount = requestCount;
		this.totalLatencyMs = totalLatencyMs;
		this.lastUpdatedAt = lastUpdatedAt;
	}

	public String getKeyId() { return keyId; }
	public String getScopeType() { return scopeType; }
	public String getScopeId() { return scopeId; }
	public LocalDate getDay() { return day; }
	public long getInputTokens() { return inputTokens; }
	public long getOutputTokens() { return outputTokens; }
	public long getReasoningTokens() { return reasoningTokens; }
	public long getRequestCount() { return requestCount; }
	public long getTotalLatencyMs() { return totalLatencyMs; }
	public Instant getLastUpdatedAt() { return lastUpdatedAt; }

	public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
	public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
	public void setReasoningTokens(long reasoningTokens) { this.reasoningTokens = reasoningTokens; }
	public void setRequestCount(long requestCount) { this.requestCount = requestCount; }
	public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
	public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

	public static class UsageRecordedTokenRollupId implements Serializable {
		private String keyId;
		private String scopeType;
		private String scopeId;
		private LocalDate day;

		public UsageRecordedTokenRollupId() {
		}

		public UsageRecordedTokenRollupId(String keyId, String scopeType, String scopeId, LocalDate day) {
			this.keyId = keyId;
			this.scopeType = scopeType;
			this.scopeId = scopeId;
			this.day = day;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof UsageRecordedTokenRollupId that)) return false;
			return Objects.equals(keyId, that.keyId)
					&& Objects.equals(scopeType, that.scopeType)
					&& Objects.equals(scopeId, that.scopeId)
					&& Objects.equals(day, that.day);
		}

		@Override
		public int hashCode() {
			return Objects.hash(keyId, scopeType, scopeId, day);
		}
	}
}
