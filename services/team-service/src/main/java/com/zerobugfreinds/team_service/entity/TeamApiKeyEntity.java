package com.zerobugfreinds.team_service.entity;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(
        name = "team_api_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_team_api_keys_team_provider_key_hash",
                        columnNames = {"team_id", "provider", "key_hash"}
                ),
                @UniqueConstraint(
                        name = "uk_team_api_keys_team_alias",
                        columnNames = {"team_id", "key_alias"}
                )
        }
)
public class TeamApiKeyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private TeamApiKeyProvider provider;

    @Column(name = "key_alias", nullable = false, length = 100)
    private String keyAlias;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Lob
    @Column(name = "encrypted_key", nullable = false)
    private String encryptedKey;

    @Column(name = "monthly_budget_usd", precision = 12, scale = 2)
    private BigDecimal monthlyBudgetUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 비어 있지 않으면 삭제 예정(유예 중). */
    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    /** 유예 종료 후 물리 삭제 예정 시각. 삭제 예정이 아니면 null. */
    @Column(name = "permanent_deletion_at")
    private Instant permanentDeletionAt;

    /** 삭제 요청 시 사용자가 선택한 유예 기간(일). 삭제 예정이 아니면 null. */
    @Column(name = "deletion_grace_days")
    private Integer deletionGraceDays;

    /** 삭제 완료 시 사용 로그 보존 여부(기본: 보존). */
    @Column(name = "retain_usage_logs", nullable = false, columnDefinition = "boolean default true")
    private boolean retainUsageLogs = true;

    protected TeamApiKeyEntity() {
    }

    public static TeamApiKeyEntity register(
            Long teamId,
            TeamApiKeyProvider provider,
            String keyAlias,
            String keyHash,
            String encryptedKey,
            BigDecimal monthlyBudgetUsd
    ) {
        TeamApiKeyEntity entity = new TeamApiKeyEntity();
        entity.teamId = teamId;
        entity.provider = provider;
        entity.keyAlias = keyAlias;
        entity.keyHash = keyHash;
        entity.encryptedKey = encryptedKey;
        entity.monthlyBudgetUsd = monthlyBudgetUsd;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void updateCredential(
            TeamApiKeyProvider provider,
            String keyAlias,
            String keyHash,
            String encryptedKey,
            BigDecimal monthlyBudgetUsd
    ) {
        this.provider = provider;
        this.keyAlias = keyAlias;
        this.keyHash = keyHash;
        this.encryptedKey = encryptedKey;
        this.monthlyBudgetUsd = monthlyBudgetUsd;
    }

    public void updateAliasAndBudget(String keyAlias, BigDecimal monthlyBudgetUsd) {
        this.keyAlias = keyAlias;
        this.monthlyBudgetUsd = monthlyBudgetUsd;
    }

    public boolean isDeletionPending() {
        return deletionRequestedAt != null;
    }

    public void markDeletionRequested(Instant now, int gracePeriodDays, boolean retainUsageLogs) {
        this.deletionRequestedAt = now;
        this.deletionGraceDays = gracePeriodDays;
        this.permanentDeletionAt = now.plus(Duration.ofDays(gracePeriodDays));
        this.retainUsageLogs = retainUsageLogs;
    }

    public void clearDeletionRequest() {
        this.deletionRequestedAt = null;
        this.permanentDeletionAt = null;
        this.deletionGraceDays = null;
        this.retainUsageLogs = true;
    }

    public Long getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public TeamApiKeyProvider getProvider() {
        return provider;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public BigDecimal getMonthlyBudgetUsd() {
        return monthlyBudgetUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public Instant getPermanentDeletionAt() {
        return permanentDeletionAt;
    }

    public Integer getDeletionGraceDays() {
        return deletionGraceDays;
    }

    public boolean isRetainUsageLogs() {
        return retainUsageLogs;
    }
}
