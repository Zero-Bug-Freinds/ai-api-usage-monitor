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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 사용자가 등록한 외부 AI API 키. 실제 키 평문은 DB에 두지 않고 암호문만 저장한다.
 * 삭제 요청 시 행은 유지되고 유예 기간 후 스케줄러가 행을 제거한다(usage 쪽 사용 로그의 apiKeyId 문자열은 별도 DB에 남음).
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

	@Column(name = "monthly_budget_usd", precision = 12, scale = 2)
	private BigDecimal monthlyBudgetUsd;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** 비어 있지 않으면 삭제 예정(유예 중). */
	@Column(name = "deletion_requested_at")
	private Instant deletionRequestedAt;

	/** 이 시각이 지나면 스케줄러가 행을 물리 삭제한다. 삭제 예정이 아니면 null. */
	@Column(name = "permanent_deletion_at")
	private Instant permanentDeletionAt;

	/** 삭제 요청 시 선택한 유예 기간(일). 삭제 예정이 아니면 null. */
	@Column(name = "deletion_grace_days")
	private Integer deletionGraceDays;

	protected ExternalApiKeyEntity() {
	}

	public static ExternalApiKeyEntity register(
			Long userId,
			ExternalApiKeyProvider provider,
			String keyAlias,
			String keyHash,
			String encryptedKey,
			BigDecimal monthlyBudgetUsd
	) {
		ExternalApiKeyEntity entity = new ExternalApiKeyEntity();
		entity.userId = userId;
		entity.provider = provider;
		entity.keyAlias = keyAlias;
		entity.keyHash = keyHash;
		entity.encryptedKey = encryptedKey;
		entity.monthlyBudgetUsd = monthlyBudgetUsd;
		entity.createdAt = Instant.now();
		return entity;
	}

	public void updateCredential(
			ExternalApiKeyProvider provider,
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

	/** 삭제 예정으로 표시한다(서비스에서 중복 여부를 검증한다). */
	public void markPendingDeletion(Instant now, int gracePeriodDays) {
		this.deletionRequestedAt = now;
		this.deletionGraceDays = gracePeriodDays;
		this.permanentDeletionAt = now.plus(Duration.ofDays(gracePeriodDays));
	}

	/** 삭제 예정을 취소한다. */
	public void clearPendingDeletion() {
		this.deletionRequestedAt = null;
		this.permanentDeletionAt = null;
		this.deletionGraceDays = null;
	}

	public boolean isPendingDeletion() {
		return this.deletionRequestedAt != null;
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
}
