package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyDeletionPolicy;
import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyResponse;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyAliasException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyAlreadyPendingDeletionException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotPendingDeletionException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotFoundException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyPendingDeletionException;
import com.zerobugfreinds.identity_service.repository.ExternalApiKeyRepository;
import com.zerobugfreinds.identity_service.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 외부 AI API 키 등록·삭제(유예 후 물리 삭제).
 */
@Service
public class ExternalApiKeyService {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyService.class);

	private final ExternalApiKeyRepository externalApiKeyRepository;
	private final EncryptionUtil encryptionUtil;

	public ExternalApiKeyService(ExternalApiKeyRepository externalApiKeyRepository, EncryptionUtil encryptionUtil) {
		this.externalApiKeyRepository = externalApiKeyRepository;
		this.encryptionUtil = encryptionUtil;
	}

	@Transactional
	public ExternalApiKeyEntity register(
			Long userId,
			ExternalApiKeyProvider provider,
			String alias,
			String plainKey,
			BigDecimal monthlyBudgetUsd
	) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		String trimmedAlias = StringUtils.hasText(alias) ? alias.trim() : "";
		if (!StringUtils.hasText(trimmedAlias)) {
			throw new IllegalArgumentException("alias는 필수입니다");
		}
		String normalizedKey = StringUtils.hasText(plainKey) ? plainKey.trim() : "";
		if (!StringUtils.hasText(normalizedKey)) {
			throw new IllegalArgumentException("externalKey는 필수입니다");
		}

		if (externalApiKeyRepository.existsByUserIdAndKeyAlias(userId, trimmedAlias)) {
			throw new DuplicateExternalApiKeyAliasException("이미 사용 중인 별칭입니다");
		}

		String keyHash = encryptionUtil.sha256HexForUniqueness(provider.name(), normalizedKey);
		long duplicateCount = externalApiKeyRepository.countByUserIdAndProviderAndKeyHashAndDeletionRequestedAtIsNull(
				userId,
				provider,
				keyHash
		);
		if (duplicateCount > 0) {
			log.warn(
					"[AUDIT] external_api_key_duplicate_detected userId={} provider={} alias={} hashPrefix={} duplicateCount={}",
					userId,
					provider.name(),
					trimmedAlias,
					keyHash.substring(0, 8),
					duplicateCount
			);
			throw new DuplicateExternalApiKeyException("이미 등록된 API 키입니다");
		}

		String encrypted = encryptionUtil.encryptAes256Gcm(normalizedKey);
		ExternalApiKeyEntity entity = ExternalApiKeyEntity.register(
				userId,
				provider,
				trimmedAlias,
				keyHash,
				encrypted,
				monthlyBudgetUsd
		);
		ExternalApiKeyEntity saved = externalApiKeyRepository.save(entity);

		log.info(
				"[AUDIT] external_api_key_registered userId={} provider={} alias={} keyId={}",
				userId,
				provider.name(),
				trimmedAlias,
				saved.getId()
		);

		return saved;
	}

	@Transactional(readOnly = true)
	public List<ExternalApiKeyEntity> getMyKeys(Long userId) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		return externalApiKeyRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public ExternalApiKeyEntity update(
			Long userId,
			Long externalKeyId,
			ExternalApiKeyProvider provider,
			String alias,
			String plainKey,
			BigDecimal monthlyBudgetUsd
	) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (externalKeyId == null) {
			throw new IllegalArgumentException("externalKeyId는 필수입니다");
		}
		String trimmedAlias = StringUtils.hasText(alias) ? alias.trim() : "";
		if (!StringUtils.hasText(trimmedAlias)) {
			throw new IllegalArgumentException("alias는 필수입니다");
		}
		String normalizedKey = StringUtils.hasText(plainKey) ? plainKey.trim() : "";

		Optional<ExternalApiKeyEntity> active = externalApiKeyRepository.findByIdAndUserIdAndDeletionRequestedAtIsNull(
				externalKeyId,
				userId
		);
		ExternalApiKeyEntity entity;
		if (active.isPresent()) {
			entity = active.get();
		} else {
			Optional<ExternalApiKeyEntity> any = externalApiKeyRepository.findByIdAndUserId(externalKeyId, userId);
			if (any.isPresent() && any.get().isPendingDeletion()) {
				throw new ExternalApiKeyPendingDeletionException("삭제 예정인 키는 수정할 수 없습니다. 취소 후 다시 시도하세요.");
			}
			throw new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다");
		}

		if (externalApiKeyRepository.existsByUserIdAndKeyAliasAndIdNot(userId, trimmedAlias, externalKeyId)) {
			throw new DuplicateExternalApiKeyAliasException("이미 사용 중인 별칭입니다");
		}

		if (StringUtils.hasText(normalizedKey)) {
			if (provider == null) {
				throw new IllegalArgumentException("externalKey를 수정할 때 provider는 필수입니다");
			}
			String keyHash = encryptionUtil.sha256HexForUniqueness(provider.name(), normalizedKey);
			if (externalApiKeyRepository.existsByUserIdAndProviderAndKeyHashAndIdNotAndDeletionRequestedAtIsNull(
					userId,
					provider,
					keyHash,
					externalKeyId
			)) {
				throw new DuplicateExternalApiKeyException("이미 등록된 API 키입니다");
			}

			String encrypted = encryptionUtil.encryptAes256Gcm(normalizedKey);
			entity.updateCredential(provider, trimmedAlias, keyHash, encrypted, monthlyBudgetUsd);
		} else {
			entity.updateAliasAndBudget(trimmedAlias, monthlyBudgetUsd);
		}

		log.info(
				"[AUDIT] external_api_key_updated userId={} provider={} alias={} keyId={}",
				userId,
				entity.getProvider().name(),
				trimmedAlias,
				entity.getId()
		);

		return entity;
	}

	@Transactional(readOnly = true)
	public InternalApiKeyResponse resolveInternalKey(Long userId, ExternalApiKeyProvider provider) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		ExternalApiKeyEntity entity = externalApiKeyRepository
				.findTopByUserIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(userId, provider)
				.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
		String plainKey = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey());
		return new InternalApiKeyResponse(plainKey, String.valueOf(entity.getId()));
	}

	/**
	 * 삭제 요청: 유예 기간 후 {@link #purgeExpiredKeys()} 가 행을 제거한다.
	 */
	@Transactional
	public ExternalApiKeyEntity requestDeletion(Long userId, Long externalKeyId) {
		if (userId == null || externalKeyId == null) {
			throw new IllegalArgumentException("userId와 externalKeyId는 필수입니다");
		}
		ExternalApiKeyEntity entity = externalApiKeyRepository.findByIdAndUserId(externalKeyId, userId)
				.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
		if (entity.isPendingDeletion()) {
			throw new ExternalApiKeyAlreadyPendingDeletionException("이미 삭제 예정인 키입니다");
		}
		Instant now = Instant.now();
		entity.markPendingDeletion(now, ExternalApiKeyDeletionPolicy.PENDING_RETENTION);
		log.info(
				"[AUDIT] external_api_key_deletion_requested userId={} keyId={} permanentDeletionAt={}",
				userId,
				entity.getId(),
				entity.getPermanentDeletionAt()
		);
		return entity;
	}

	@Transactional
	public ExternalApiKeyEntity cancelDeletion(Long userId, Long externalKeyId) {
		if (userId == null || externalKeyId == null) {
			throw new IllegalArgumentException("userId와 externalKeyId는 필수입니다");
		}
		ExternalApiKeyEntity entity = externalApiKeyRepository.findByIdAndUserId(externalKeyId, userId)
				.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
		if (!entity.isPendingDeletion()) {
			throw new ExternalApiKeyNotPendingDeletionException("삭제 예정 상태가 아닙니다");
		}
		entity.clearPendingDeletion();
		log.info("[AUDIT] external_api_key_deletion_cancelled userId={} keyId={}", userId, entity.getId());
		return entity;
	}

	/**
	 * 유예 종료된 키 행을 물리 삭제한다. usage DB의 사용 로그는 별도 테이블·문자열 apiKeyId 로 보존된다.
	 */
	@Transactional
	public int purgeExpiredKeys() {
		Instant now = Instant.now();
		List<ExternalApiKeyEntity> expired = externalApiKeyRepository.findAllByPermanentDeletionAtIsNotNullAndPermanentDeletionAtBefore(now);
		for (ExternalApiKeyEntity e : expired) {
			log.info(
					"[AUDIT] external_api_key_purged userId={} keyId={} provider={}",
					e.getUserId(),
					e.getId(),
					e.getProvider().name()
			);
		}
		externalApiKeyRepository.deleteAll(expired);
		return expired.size();
	}
}
