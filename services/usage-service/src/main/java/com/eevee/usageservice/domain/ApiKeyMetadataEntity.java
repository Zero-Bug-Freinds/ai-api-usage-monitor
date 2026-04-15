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
