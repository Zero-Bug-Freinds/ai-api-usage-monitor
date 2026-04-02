package com.zerobugfreinds.identity_service.entity;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
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

/**
 * 사용자가 등록한 외부 AI API 키. 실제 키 평문은 DB에 두지 않고 암호문만 저장한다.
 */
@Entity
@Table(
		name = "external_api_keys",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_external_api_keys_user_provider_key_hash",
				columnNames = {"user_id", "provider", "key_hash"}
		)
)
public class ExternalApiKeyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 32)
	private ExternalApiKeyProvider provider;

	@Column(name = "key_alias", nullable = false, length = 100)
	private String keyAlias;

	@Column(name = "key_hash", nullable = false, length = 64)
	private String keyHash;

	@Lob
	@Column(name = "encrypted_key", nullable = false)
	private String encryptedKey;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ExternalApiKeyEntity() {
	}

	public static ExternalApiKeyEntity register(
			Long userId,
			ExternalApiKeyProvider provider,
			String keyAlias,
			String keyHash,
			String encryptedKey
	) {
		ExternalApiKeyEntity entity = new ExternalApiKeyEntity();
		entity.userId = userId;
		entity.provider = provider;
		entity.keyAlias = keyAlias;
		entity.keyHash = keyHash;
		entity.encryptedKey = encryptedKey;
		entity.createdAt = Instant.now();
		return entity;
	}

	public void updateCredential(
			ExternalApiKeyProvider provider,
			String keyAlias,
			String keyHash,
			String encryptedKey
	) {
		this.provider = provider;
		this.keyAlias = keyAlias;
		this.keyHash = keyHash;
		this.encryptedKey = encryptedKey;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public ExternalApiKeyProvider getProvider() {
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
