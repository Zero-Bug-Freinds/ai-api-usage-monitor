package com.zerobugfreinds.ai_agent_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "identity_api_key_projection")
@IdClass(IdentityApiKeySnapshotEntity.IdentityApiKeySnapshotId.class)
public class IdentityApiKeySnapshotEntity {

	@Id
	@Column(nullable = false, length = 255)
	private String userId;

	@Id
	@Column(nullable = false)
	private Long keyId;

	@Column(length = 255)
	private String alias;

	@Column(length = 80)
	private String provider;

	@Column(length = 80)
	private String visibility;

	@Column(length = 40)
	private String status;

	private BigDecimal monthlyBudgetUsd;

	@Column(name = "key_hash", length = 64)
	private String keyHash;

	@Column(nullable = false)
	private Instant updatedAt;

	protected IdentityApiKeySnapshotEntity() {
	}

	public IdentityApiKeySnapshotEntity(
			String userId,
			Long keyId,
			String alias,
			String provider,
			String visibility,
			String status,
			BigDecimal monthlyBudgetUsd,
			String keyHash,
			Instant updatedAt
	) {
		this.userId = userId;
		this.keyId = keyId;
		this.alias = alias;
		this.provider = provider;
		this.visibility = visibility;
		this.status = status;
		this.monthlyBudgetUsd = monthlyBudgetUsd;
		this.keyHash = keyHash;
		this.updatedAt = updatedAt;
	}

	public String getUserId() { return userId; }
	public Long getKeyId() { return keyId; }
	public String getAlias() { return alias; }
	public String getProvider() { return provider; }
	public String getVisibility() { return visibility; }
	public String getStatus() { return status; }
	public BigDecimal getMonthlyBudgetUsd() { return monthlyBudgetUsd; }
	public String getKeyHash() { return keyHash; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void setAlias(String alias) { this.alias = alias; }
	public void setProvider(String provider) { this.provider = provider; }
	public void setVisibility(String visibility) { this.visibility = visibility; }
	public void setStatus(String status) { this.status = status; }
	public void setMonthlyBudgetUsd(BigDecimal monthlyBudgetUsd) { this.monthlyBudgetUsd = monthlyBudgetUsd; }
	public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

	public static class IdentityApiKeySnapshotId implements Serializable {
		private String userId;
		private Long keyId;

		public IdentityApiKeySnapshotId() {
		}

		public IdentityApiKeySnapshotId(String userId, Long keyId) {
			this.userId = userId;
			this.keyId = keyId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof IdentityApiKeySnapshotId that)) return false;
			return Objects.equals(userId, that.userId) && Objects.equals(keyId, that.keyId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(userId, keyId);
		}
	}
}
