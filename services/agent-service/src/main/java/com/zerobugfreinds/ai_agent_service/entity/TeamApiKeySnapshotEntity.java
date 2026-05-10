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
@Table(name = "team_api_key_projection")
@IdClass(TeamApiKeySnapshotEntity.TeamApiKeySnapshotId.class)
public class TeamApiKeySnapshotEntity {

	@Id
	@Column(nullable = false)
	private Long teamId;

	@Id
	@Column(nullable = false)
	private Long teamApiKeyId;

	@Column(length = 255)
	private String teamName;

	@Column(length = 120)
	private String ownerUserId;

	@Column(length = 80)
	private String visibility;

	@Column(length = 255)
	private String alias;

	@Column(length = 80)
	private String provider;

	@Column(length = 40)
	private String status;

	private Boolean retainLogs;

	@Column(name = "key_hash", length = 64)
	private String keyHash;

	@Column(nullable = false)
	private Instant updatedAt;

	protected TeamApiKeySnapshotEntity() {
	}

	public TeamApiKeySnapshotEntity(
			Long teamId,
			Long teamApiKeyId,
			String teamName,
			String ownerUserId,
			String visibility,
			String alias,
			String provider,
			String status,
			Boolean retainLogs,
			String keyHash,
			Instant updatedAt
	) {
		this.teamId = teamId;
		this.teamApiKeyId = teamApiKeyId;
		this.teamName = teamName;
		this.ownerUserId = ownerUserId;
		this.visibility = visibility;
		this.alias = alias;
		this.provider = provider;
		this.status = status;
		this.retainLogs = retainLogs;
		this.keyHash = keyHash;
		this.updatedAt = updatedAt;
	}

	public Long getTeamId() { return teamId; }
	public Long getTeamApiKeyId() { return teamApiKeyId; }
	public String getTeamName() { return teamName; }
	public String getOwnerUserId() { return ownerUserId; }
	public String getVisibility() { return visibility; }
	public String getAlias() { return alias; }
	public String getProvider() { return provider; }
	public String getStatus() { return status; }
	public Boolean getRetainLogs() { return retainLogs; }
	public String getKeyHash() { return keyHash; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void setTeamName(String teamName) { this.teamName = teamName; }
	public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
	public void setVisibility(String visibility) { this.visibility = visibility; }
	public void setAlias(String alias) { this.alias = alias; }
	public void setProvider(String provider) { this.provider = provider; }
	public void setStatus(String status) { this.status = status; }
	public void setRetainLogs(Boolean retainLogs) { this.retainLogs = retainLogs; }
	public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

	public static class TeamApiKeySnapshotId implements Serializable {
		private Long teamId;
		private Long teamApiKeyId;

		public TeamApiKeySnapshotId() {
		}

		public TeamApiKeySnapshotId(Long teamId, Long teamApiKeyId) {
			this.teamId = teamId;
			this.teamApiKeyId = teamApiKeyId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof TeamApiKeySnapshotId that)) return false;
			return Objects.equals(teamId, that.teamId) && Objects.equals(teamApiKeyId, that.teamApiKeyId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(teamId, teamApiKeyId);
		}
	}
}
