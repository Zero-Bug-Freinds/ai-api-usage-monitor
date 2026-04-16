package com.eevee.usageservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "api_key_metadata")
public class ApiKeyMetadataEntity {

    @Id
    @Column(name = "key_id")
    private String keyId;

    @Column(nullable = false)
    private String userId;

    private String provider;

    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyStatus status;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ApiKeyMetadataEntity() {
    }

    public static ApiKeyMetadataEntity create(String keyId, String userId) {
        ApiKeyMetadataEntity entity = new ApiKeyMetadataEntity();
        entity.keyId = keyId;
        entity.userId = userId;
        entity.updatedAt = Instant.now();
        entity.status = ApiKeyStatus.ACTIVE;
        return entity;
    }

    public void apply(
            String userId,
            String provider,
            String alias,
            ApiKeyStatus status,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.provider = provider;
        this.alias = alias;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getUserId() {
        return userId;
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
