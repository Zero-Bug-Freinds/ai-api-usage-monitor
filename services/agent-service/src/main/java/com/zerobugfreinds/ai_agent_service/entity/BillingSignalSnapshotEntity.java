package com.zerobugfreinds.ai_agent_service.entity;

import java.math.BigDecimal;
import java.time.Instant;

public class BillingSignalSnapshotEntity {

	private String apiKeyId;

	private String userId;

	private String teamId;

	private String subjectType;

	private BigDecimal latestEstimatedCostUsd;
	private Instant latestFinalizedAt;

	private String provider;

	private String model;

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

	public String getApiKeyId() { return apiKeyId; }
	public String getUserId() { return userId; }
	public String getTeamId() { return teamId; }
	public String getSubjectType() { return subjectType; }
	public BigDecimal getLatestEstimatedCostUsd() { return latestEstimatedCostUsd; }
	public Instant getLatestFinalizedAt() { return latestFinalizedAt; }
	public String getProvider() { return provider; }
	public String getModel() { return model; }
	public String getPricingRuleVersion() { return pricingRuleVersion; }

	public void setUserId(String userId) { this.userId = userId; }
	public void setTeamId(String teamId) { this.teamId = teamId; }
	public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
	public void setLatestEstimatedCostUsd(BigDecimal latestEstimatedCostUsd) { this.latestEstimatedCostUsd = latestEstimatedCostUsd; }
	public void setLatestFinalizedAt(Instant latestFinalizedAt) { this.latestFinalizedAt = latestFinalizedAt; }
	public void setProvider(String provider) { this.provider = provider; }
	public void setModel(String model) { this.model = model; }
	public void setPricingRuleVersion(String pricingRuleVersion) { this.pricingRuleVersion = pricingRuleVersion; }
}
