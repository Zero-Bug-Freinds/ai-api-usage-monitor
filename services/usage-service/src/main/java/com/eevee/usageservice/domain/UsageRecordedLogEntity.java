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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "usage_recorded_log",
        indexes = {
                @Index(name = "idx_url_team_occurred", columnList = "team_id, occurred_at"),
                @Index(name = "idx_url_user_team_occurred", columnList = "user_id, team_id, occurred_at")
        }
)
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

    @Column(name = "team_api_key_id")
    private String teamApiKeyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
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

    @Column(name = "estimated_reasoning_tokens")
    private Long estimatedReasoningTokens;

    /**
     * Provider-specific token breakdown, stored as JSONB.
     * Example (OpenAI):
     * {
     *   "prompt_cached_tokens": 1,
     *   "prompt_audio_tokens": 2,
     *   "completion_reasoning_tokens": 11,
     *   "completion_audio_tokens": 3,
     *   "completion_accepted_prediction_tokens": 5,
     *   "completion_rejected_prediction_tokens": 7
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_token_details", columnDefinition = "jsonb")
    private String providerTokenDetails;

    /** USD; matches billing output. {@code NUMERIC(18,10)} — small per-call costs stay distinguishable after sum. */
    @Column(precision = 18, scale = 10)
    private BigDecimal estimatedCost;

    private String requestPath;

    private String upstreamHost;

    @Column(name = "latency_ms")
    private Long latencyMs;

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
            String teamApiKeyId,
            String apiKeyFingerprint,
            String apiKeySource,
            AiProvider provider,
            String model,
            Long promptTokens,
            Long completionTokens,
            Long totalTokens,
            Long estimatedReasoningTokens,
            String providerTokenDetails,
            BigDecimal estimatedCost,
            String requestPath,
            String upstreamHost,
            Long latencyMs,
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
        this.teamApiKeyId = teamApiKeyId;
        this.apiKeyFingerprint = apiKeyFingerprint;
        this.apiKeySource = apiKeySource;
        this.provider = provider;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.estimatedReasoningTokens = estimatedReasoningTokens;
        this.providerTokenDetails = providerTokenDetails;
        this.estimatedCost = estimatedCost;
        this.requestPath = requestPath;
        this.upstreamHost = upstreamHost;
        this.latencyMs = latencyMs;
        this.streaming = streaming;
        this.requestSuccessful = requestSuccessful;
        this.upstreamStatusCode = upstreamStatusCode;
        this.persistedAt = persistedAt;
    }

    public UsageRecordedLogEntity(
            UUID eventId,
            Instant occurredAt,
            String correlationId,
            String userId,
            String organizationId,
            String teamId,
            String apiKeyId,
            String teamApiKeyId,
            String apiKeyFingerprint,
            String apiKeySource,
            AiProvider provider,
            String model,
            Long promptTokens,
            Long completionTokens,
            Long totalTokens,
            Long estimatedReasoningTokens,
            String providerTokenDetails,
            BigDecimal estimatedCost,
            String requestPath,
            String upstreamHost,
            Boolean streaming,
            boolean requestSuccessful,
            Integer upstreamStatusCode,
            Instant persistedAt
    ) {
        this(
                eventId,
                occurredAt,
                correlationId,
                userId,
                organizationId,
                teamId,
                apiKeyId,
                teamApiKeyId,
                apiKeyFingerprint,
                apiKeySource,
                provider,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                estimatedReasoningTokens,
                providerTokenDetails,
                estimatedCost,
                requestPath,
                upstreamHost,
                null,
                streaming,
                requestSuccessful,
                upstreamStatusCode,
                persistedAt
        );
    }

    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
    public String getUserId() { return userId; }
    public String getOrganizationId() { return organizationId; }
    public String getTeamId() { return teamId; }
    public String getApiKeyId() { return apiKeyId; }
    public String getTeamApiKeyId() { return teamApiKeyId; }
    public ApiKeyMetadataEntity getApiKeyMetadata() { return apiKeyMetadata; }
    public String getApiKeyFingerprint() { return apiKeyFingerprint; }
    public String getApiKeySource() { return apiKeySource; }
    public AiProvider getProvider() { return provider; }
    public String getModel() { return model; }
    public Long getPromptTokens() { return promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public Long getEstimatedReasoningTokens() { return estimatedReasoningTokens; }
    public String getProviderTokenDetails() { return providerTokenDetails; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getRequestPath() { return requestPath; }
    public String getUpstreamHost() { return upstreamHost; }
    public Long getLatencyMs() { return latencyMs; }
    public Boolean getStreaming() { return streaming; }
    public boolean isRequestSuccessful() { return requestSuccessful; }
    public Integer getUpstreamStatusCode() { return upstreamStatusCode; }
    public Instant getPersistedAt() { return persistedAt; }
}
