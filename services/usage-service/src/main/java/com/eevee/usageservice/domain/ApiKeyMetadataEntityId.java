package com.eevee.usageservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ApiKeyMetadataEntityId implements Serializable {

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_scope", nullable = false, length = 16)
    private ApiKeyMetadataScope keyScope;

    protected ApiKeyMetadataEntityId() {
    }

    public ApiKeyMetadataEntityId(String keyId, String userId, ApiKeyMetadataScope keyScope) {
        this.keyId = Objects.requireNonNull(keyId);
        this.userId = Objects.requireNonNull(userId);
        this.keyScope = Objects.requireNonNull(keyScope);
    }

    public static ApiKeyMetadataEntityId personal(String keyId, String userId) {
        return new ApiKeyMetadataEntityId(keyId, userId, ApiKeyMetadataScope.PERSONAL);
    }

    public static ApiKeyMetadataEntityId team(String teamApiKeyId, String memberUserId) {
        return new ApiKeyMetadataEntityId(teamApiKeyId, memberUserId, ApiKeyMetadataScope.TEAM);
    }

    public String getKeyId() {
        return keyId;
    }

    public String getUserId() {
        return userId;
    }

    public ApiKeyMetadataScope getKeyScope() {
        return keyScope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApiKeyMetadataEntityId that = (ApiKeyMetadataEntityId) o;
        return Objects.equals(keyId, that.keyId)
                && Objects.equals(userId, that.userId)
                && keyScope == that.keyScope;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyId, userId, keyScope);
    }
}
