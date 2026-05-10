package com.zerobugfreinds.ai_agent_service.entity;

import com.zerobugfreinds.ai_agent_service.dto.RecommendationScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
		name = "recommendation_projection",
		indexes = {
				@Index(name = "idx_reco_projection_scope_key", columnList = "scopeType, scopeId, keyId"),
				@Index(name = "idx_reco_projection_generated_at", columnList = "generatedAt")
		}
)
public class RecommendationSnapshotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RecommendationScopeType scopeType;

	@Column(nullable = false, length = 120)
	private String scopeId;

	@Column(nullable = false, length = 120)
	private String keyId;

	@Column(nullable = false, length = 40)
	private String status;

	@Column(nullable = false)
	private Instant generatedAt;

	@Lob
	@Column(nullable = false)
	private String payloadJson;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected RecommendationSnapshotEntity() {
	}

	public RecommendationSnapshotEntity(
			RecommendationScopeType scopeType,
			String scopeId,
			String keyId,
			String status,
			Instant generatedAt,
			String payloadJson,
			Instant createdAt
	) {
		this.scopeType = scopeType;
		this.scopeId = scopeId;
		this.keyId = keyId;
		this.status = status;
		this.generatedAt = generatedAt;
		this.payloadJson = payloadJson;
		this.createdAt = createdAt;
	}

	public RecommendationScopeType getScopeType() {
		return scopeType;
	}

	public String getScopeId() {
		return scopeId;
	}

	public String getKeyId() {
		return keyId;
	}

	public String getPayloadJson() {
		return payloadJson;
	}
}
