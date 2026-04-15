package com.eevee.usageservice.domain;

import com.eevee.usage.events.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_recorded_log")
public class UsageRecordedLogEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID eventId;

    @Column(nullable = false)
    private Instant occurredAt;

    private String correlationId;

    @Column(nullable = false)
    private String userId;

    private String organizationId;

    private String teamId;

    @Column(name = "api_key_id")
    private String apiKeyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "api_key_id",
            referencedColumnName = "key_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private ApiKeyMetadataEntity apiKeyMetadata;

    private String apiKeyFingerprint;

    private String apiKeySource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiProvider provider;

    private String model;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    @Column(precision = 19, scale = 4)
    private BigDecimal estimatedCost;

    private String requestPath;

    private String upstreamHost;

    private Boolean streaming;

    @Column(name = "request_successful", nullable = false)
    private boolean requestSuccessful = true;

    @Column(name = "upstream_status_code")
    private Integer upstreamStatusCode;

    @Column(nullable = false)
    private Instant persistedAt;

    protected UsageRecordedLogEntity() {
    }

    public UsageRecordedLogEntity(
            UUID eventId,
            Instant occurredAt,
            String correlationId,
            String userId,
            String organizationId,
            String teamId,
            String apiKeyId,
            String apiKeyFingerprint,
            String apiKeySource,
            AiProvider provider,
            String model,
            Long promptTokens,
            Long completionTokens,
            Long totalTokens,
            BigDecimal estimatedCost,
            String requestPath,
            String upstreamHost,
            Boolean streaming,
            boolean requestSuccessful,
            Integer upstreamStatusCode,
            Instant persistedAt
    ) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.userId = userId;
        this.organizationId = organizationId;
        this.teamId = teamId;
        this.apiKeyId = apiKeyId;
        this.apiKeyFingerprint = apiKeyFingerprint;
        this.apiKeySource = apiKeySource;
        this.provider = provider;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.estimatedCost = estimatedCost;
        this.requestPath = requestPath;
        this.upstreamHost = upstreamHost;
        this.streaming = streaming;
        this.requestSuccessful = requestSuccessful;
        this.upstreamStatusCode = upstreamStatusCode;
        this.persistedAt = persistedAt;
    }

    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
    public String getUserId() { return userId; }
    public String getOrganizationId() { return organizationId; }
    public String getTeamId() { return teamId; }
    public String getApiKeyId() { return apiKeyId; }
    public ApiKeyMetadataEntity getApiKeyMetadata() { return apiKeyMetadata; }
    public String getApiKeyFingerprint() { return apiKeyFingerprint; }
    public String getApiKeySource() { return apiKeySource; }
    public AiProvider getProvider() { return provider; }
    public String getModel() { return model; }
    public Long getPromptTokens() { return promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getRequestPath() { return requestPath; }
    public String getUpstreamHost() { return upstreamHost; }
    public Boolean getStreaming() { return streaming; }
    public boolean isRequestSuccessful() { return requestSuccessful; }
    public Integer getUpstreamStatusCode() { return upstreamStatusCode; }
    public Instant getPersistedAt() { return persistedAt; }
}
