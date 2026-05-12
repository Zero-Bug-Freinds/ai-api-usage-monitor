package com.eevee.usageservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "api_key_metadata")
public class ApiKeyMetadataEntity {

    @EmbeddedId
    private ApiKeyMetadataEntityId id;

    @Column(name = "team_id")
    private String teamId;

    private String provider;

    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyStatus status;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ApiKeyMetadataEntity() {
    }

    public static ApiKeyMetadataEntity createPersonal(String keyId, String userId) {
        ApiKeyMetadataEntity entity = new ApiKeyMetadataEntity();
        entity.id = ApiKeyMetadataEntityId.personal(keyId, userId);
        entity.teamId = null;
        entity.updatedAt = Instant.now();
        entity.status = ApiKeyStatus.ACTIVE;
        return entity;
    }

    /**
     * One row per team member: {@code userId} in the PK is the member who should see this key in filters.
     */
    public static ApiKeyMetadataEntity createTeamRow(String teamApiKeyId, String memberUserId, String teamId) {
        ApiKeyMetadataEntity entity = new ApiKeyMetadataEntity();
        entity.id = ApiKeyMetadataEntityId.team(teamApiKeyId, memberUserId);
        entity.teamId = teamId;
        entity.updatedAt = Instant.now();
        entity.status = ApiKeyStatus.ACTIVE;
        return entity;
    }

    /**
     * Updates non-key fields. Does not change {@link #id}; caller must load the correct composite row.
     */
    public void apply(String teamId, String provider, String alias, ApiKeyStatus status, Instant updatedAt) {
        this.teamId = teamId;
        this.provider = provider;
        this.alias = alias;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public ApiKeyMetadataEntityId getId() {
        return id;
    }

    public String getKeyId() {
        return id != null ? id.getKeyId() : null;
    }

    public String getUserId() {
        return id != null ? id.getUserId() : null;
    }

    public ApiKeyMetadataScope getKeyScope() {
        return id != null ? id.getKeyScope() : null;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getProvider() {
        return provider;
    }

    public String getAlias() {
        return alias;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
