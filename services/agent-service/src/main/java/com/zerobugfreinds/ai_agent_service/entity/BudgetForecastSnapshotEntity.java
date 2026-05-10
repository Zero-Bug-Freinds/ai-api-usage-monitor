package com.zerobugfreinds.ai_agent_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
		name = "budget_forecast_projection",
		indexes = {
				@Index(name = "idx_budget_projection_scope_key", columnList = "scopeType, scopeId, keyId"),
				@Index(name = "idx_budget_projection_generated_at", columnList = "generatedAt")
		}
)
public class BudgetForecastSnapshotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String scopeType;

	@Column(nullable = false, length = 120)
	private String scopeId;

	@Column(length = 120)
	private String keyId;

	@Column(nullable = false)
	private Instant generatedAt;

	@Lob
	@Column(nullable = false)
	private String requestJson;

	@Lob
	@Column(nullable = false)
	private String responseJson;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected BudgetForecastSnapshotEntity() {
	}

	public BudgetForecastSnapshotEntity(
			String scopeType,
			String scopeId,
			String keyId,
			Instant generatedAt,
			String requestJson,
			String responseJson,
			Instant createdAt
	) {
		this.scopeType = scopeType;
		this.scopeId = scopeId;
		this.keyId = keyId;
		this.generatedAt = generatedAt;
		this.requestJson = requestJson;
		this.responseJson = responseJson;
		this.createdAt = createdAt;
	}
}
