package com.zerobugfreinds.ai_agent_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_signal_projection")
public class BillingSignalSnapshotEntity {

	@Id
	@Column(nullable = false, length = 120)
	private String apiKeyId;

	@Column(length = 120)
	private String userId;

	@Column(length = 120)
	private String teamId;

	@Column(length = 40)
	private String subjectType;

	private BigDecimal latestEstimatedCostUsd;

	private Instant latestFinalizedAt;

	@Column(length = 80)
	private String provider;

	@Column(length = 255)
	private String model;

	@Column(length = 80)
	private String pricingRuleVersion;

	protected BillingSignalSnapshotEntity() {
	}

	public BillingSignalSnapshotEntity(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			BigDecimal latestEstimatedCostUsd,
			Instant latestFinalizedAt,
			String provider,
			String model,
			String pricingRuleVersion
	) {
		this.apiKeyId = apiKeyId;
		this.userId = userId;
		this.teamId = teamId;
		this.subjectType = subjectType;
		this.latestEstimatedCostUsd = latestEstimatedCostUsd;
		this.latestFinalizedAt = latestFinalizedAt;
		this.provider = provider;
		this.model = model;
		this.pricingRuleVersion = pricingRuleVersion;
	}

	public String getApiKeyId() {
		return apiKeyId;
	}

	public String getUserId() {
		return userId;
	}

	public String getTeamId() {
		return teamId;
	}

	public String getSubjectType() {
		return subjectType;
	}

	public BigDecimal getLatestEstimatedCostUsd() {
		return latestEstimatedCostUsd;
	}

	public Instant getLatestFinalizedAt() {
		return latestFinalizedAt;
	}

	public String getProvider() {
		return provider;
	}

	public String getModel() {
		return model;
	}

	public String getPricingRuleVersion() {
		return pricingRuleVersion;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public void setTeamId(String teamId) {
		this.teamId = teamId;
	}

	public void setSubjectType(String subjectType) {
		this.subjectType = subjectType;
	}

	public void setLatestEstimatedCostUsd(BigDecimal latestEstimatedCostUsd) {
		this.latestEstimatedCostUsd = latestEstimatedCostUsd;
	}

	public void setLatestFinalizedAt(Instant latestFinalizedAt) {
		this.latestFinalizedAt = latestFinalizedAt;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public void setPricingRuleVersion(String pricingRuleVersion) {
		this.pricingRuleVersion = pricingRuleVersion;
	}
}
