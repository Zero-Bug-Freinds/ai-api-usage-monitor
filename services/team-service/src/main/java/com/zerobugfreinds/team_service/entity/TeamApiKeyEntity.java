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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TeamApiKeyEntity() {
    }

    public static TeamApiKeyEntity register(
            Long teamId,
            TeamApiKeyProvider provider,
            String keyAlias,
            String keyHash,
            String encryptedKey
    ) {
        TeamApiKeyEntity entity = new TeamApiKeyEntity();
        entity.teamId = teamId;
        entity.provider = provider;
        entity.keyAlias = keyAlias;
        entity.keyHash = keyHash;
        entity.encryptedKey = encryptedKey;
        entity.createdAt = Instant.now();
        return entity;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
