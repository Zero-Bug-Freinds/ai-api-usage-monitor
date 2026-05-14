package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyBudgetChangedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatus;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyHashEntry;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyLookupResponse;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyResponse;
import com.zerobugfreinds.identity_service.dto.InternalFingerprintLookupResponse;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.AmbiguousExternalApiKeyHashException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyAliasException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyAlreadyPendingDeletionException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotPendingDeletionException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotFoundException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyPendingDeletionException;
import com.zerobugfreinds.identity_service.repository.ExternalApiKeyRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.zerobugfreinds.identity_service.domain.ExternalApiKeyDeletionPolicy.DEFAULT_GRACE_DAYS;
import static com.zerobugfreinds.identity_service.domain.ExternalApiKeyDeletionPolicy.MAX_GRACE_DAYS;
import static com.zerobugfreinds.identity_service.domain.ExternalApiKeyDeletionPolicy.MIN_GRACE_DAYS;

/**
 * 외부 AI API 키 등록·삭제(유예 후 물리 삭제).
 */
@Service
public class ExternalApiKeyService {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyService.class);

	private final ExternalApiKeyRepository externalApiKeyRepository;
	private final UserRepository userRepository;
	private final EncryptionUtil encryptionUtil;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final TeamApiKeyLookupClient teamApiKeyLookupClient;
	private final ApiKeyFingerprintRegistrationLock apiKeyFingerprintRegistrationLock;

	public ExternalApiKeyService(
			ExternalApiKeyRepository externalApiKeyRepository,
			UserRepository userRepository,
			EncryptionUtil encryptionUtil,
			ApplicationEventPublisher applicationEventPublisher,
			TeamApiKeyLookupClient teamApiKeyLookupClient,
			ApiKeyFingerprintRegistrationLock apiKeyFingerprintRegistrationLock
	) {
		this.externalApiKeyRepository = externalApiKeyRepository;
		this.userRepository = userRepository;
		this.encryptionUtil = encryptionUtil;
		this.applicationEventPublisher = applicationEventPublisher;
		this.teamApiKeyLookupClient = teamApiKeyLookupClient;
		this.apiKeyFingerprintRegistrationLock = apiKeyFingerprintRegistrationLock;
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
		ExternalApiKeyProvider normalizedProvider = normalizeProvider(provider);
		String trimmedAlias = StringUtils.hasText(alias) ? alias.trim() : "";
		if (!StringUtils.hasText(trimmedAlias)) {
			throw new IllegalArgumentException("alias는 필수입니다");
		}
		String normalizedKey = StringUtils.hasText(plainKey) ? plainKey.trim() : "";
		if (!StringUtils.hasText(normalizedKey)) {
			throw new IllegalArgumentException("externalKey는 필수입니다");
		}
		if (monthlyBudgetUsd == null) {
			throw new IllegalArgumentException("monthlyBudgetUsd는 필수입니다");
		}

		String keyHash = encryptionUtil.sha256HexForUniqueness(normalizedProvider.name(), normalizedKey);
		String apiKeyFingerprint = encryptionUtil.sha256HexUtf8(normalizedKey);
		return apiKeyFingerprintRegistrationLock.runWithLock(apiKeyFingerprint, () ->
				registerWithPlainTextCredential(
						userId,
						normalizedProvider,
						trimmedAlias,
						normalizedKey,
						keyHash,
						apiKeyFingerprint,
						monthlyBudgetUsd
				)
		);
	}

	private ExternalApiKeyEntity registerWithPlainTextCredential(
			Long userId,
			ExternalApiKeyProvider normalizedProvider,
			String trimmedAlias,
			String normalizedKey,
			String keyHash,
			String apiKeyFingerprint,
			BigDecimal monthlyBudgetUsd
	) {
		verifyNoTeamScopeDuplicate(normalizedProvider, apiKeyFingerprint);
		Optional<ExternalApiKeyEntity> existingSameHash =
				externalApiKeyRepository.findByUserIdAndProviderAndKeyHash(userId, normalizedProvider, keyHash);
		if (existingSameHash.isPresent()) {
			ExternalApiKeyEntity existing = existingSameHash.get();
			if (existing.isPendingDeletion()) {
				if (externalApiKeyRepository.existsByUserIdAndKeyAliasAndIdNot(userId, trimmedAlias, existing.getId())) {
					throw new DuplicateExternalApiKeyAliasException("이미 사용 중인 별칭입니다");
				}
				String encrypted = encryptionUtil.encryptAes256Gcm(normalizedKey);
				existing.clearPendingDeletion();
				existing.updateCredential(normalizedProvider, trimmedAlias, keyHash, apiKeyFingerprint, encrypted, monthlyBudgetUsd);
				ExternalApiKeyEntity reactivated;
				try {
					reactivated = externalApiKeyRepository.saveAndFlush(existing);
				} catch (DataIntegrityViolationException ex) {
					throw toDuplicateException(userId, normalizedProvider, trimmedAlias, keyHash, ex);
				}
				log.info(
						"[AUDIT] external_api_key_reactivated userId={} provider={} alias={} keyId={}",
						userId,
						normalizedProvider.name(),
						trimmedAlias,
						reactivated.getId()
				);
				publishExternalApiKeyStatusChanged(reactivated, ExternalApiKeyStatus.ACTIVE);
				publishExternalApiKeyBudgetChanged(reactivated, ExternalApiKeyStatus.ACTIVE);
				return reactivated;
			}
			log.warn(
					"[AUDIT] external_api_key_duplicate_detected userId={} provider={} alias={} hashPrefix={}",
					userId,
					normalizedProvider.name(),
					trimmedAlias,
					keyHash.substring(0, 8)
			);
			throw new DuplicateExternalApiKeyException("이미 등록된 API 키입니다");
		}
		if (externalApiKeyRepository.existsByUserIdAndKeyAlias(userId, trimmedAlias)) {
			throw new DuplicateExternalApiKeyAliasException("이미 사용 중인 별칭입니다");
		}

		String encrypted = encryptionUtil.encryptAes256Gcm(normalizedKey);
		ExternalApiKeyEntity entity = ExternalApiKeyEntity.register(
				userId,
				normalizedProvider,
				trimmedAlias,
				keyHash,
				apiKeyFingerprint,
				encrypted,
				monthlyBudgetUsd
		);
		ExternalApiKeyEntity saved;
		try {
			saved = externalApiKeyRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException ex) {
			throw toDuplicateException(userId, normalizedProvider, trimmedAlias, keyHash, ex);
		}

		log.info(
				"[AUDIT] external_api_key_registered userId={} provider={} alias={} keyId={}",
				userId,
				normalizedProvider.name(),
				trimmedAlias,
				saved.getId()
		);
		publishExternalApiKeyStatusChanged(saved, ExternalApiKeyStatus.ACTIVE);
		publishExternalApiKeyBudgetChanged(saved, ExternalApiKeyStatus.ACTIVE);

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
		if (monthlyBudgetUsd == null) {
			throw new IllegalArgumentException("monthlyBudgetUsd는 필수입니다");
		}

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
			ExternalApiKeyProvider normalizedProvider = normalizeProvider(provider);
			String keyHash = encryptionUtil.sha256HexForUniqueness(normalizedProvider.name(), normalizedKey);
			String apiKeyFingerprint = encryptionUtil.sha256HexUtf8(normalizedKey);
			apiKeyFingerprintRegistrationLock.runWithLock(apiKeyFingerprint, () -> {
				verifyNoTeamScopeDuplicate(normalizedProvider, apiKeyFingerprint);
				Optional<ExternalApiKeyEntity> otherSameHash =
						externalApiKeyRepository.findByUserIdAndProviderAndKeyHash(userId, normalizedProvider, keyHash);
				if (otherSameHash.isPresent() && !otherSameHash.get().getId().equals(externalKeyId)) {
					if (otherSameHash.get().isPendingDeletion()) {
						throw new DuplicateExternalApiKeyException("삭제예정키와 중복된 키");
					}
					throw new DuplicateExternalApiKeyException("이미 등록된 API 키입니다");
				}
				String encrypted = encryptionUtil.encryptAes256Gcm(normalizedKey);
				entity.updateCredential(normalizedProvider, trimmedAlias, keyHash, apiKeyFingerprint, encrypted, monthlyBudgetUsd);
			});
		} else {
			entity.updateAliasAndBudget(trimmedAlias, monthlyBudgetUsd);
		}
		try {
			externalApiKeyRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException ex) {
			ExternalApiKeyProvider providerForCheck = entity.getProvider();
			String keyHashForCheck = entity.getKeyHash();
			throw toDuplicateException(userId, providerForCheck, trimmedAlias, keyHashForCheck, ex);
		}

		log.info(
				"[AUDIT] external_api_key_updated userId={} provider={} alias={} keyId={}",
				userId,
				providerName(entity.getProvider()),
				trimmedAlias,
				entity.getId()
		);
		publishExternalApiKeyStatusChanged(entity, ExternalApiKeyStatus.ACTIVE);
		publishExternalApiKeyBudgetChanged(entity, ExternalApiKeyStatus.ACTIVE);

		return entity;
	}

	/**
	 * Proxy 등 내부 호출자가 클라이언트에게서 받은 외부 API 키의 해시값으로
	 * 어느 사용자의 어떤 키인지 역추적할 때 사용한다.
	 *
	 * <p>해시는 등록 시 사용한 {@link com.zerobugfreinds.identity_service.util.EncryptionUtil#sha256HexForUniqueness}
	 * 와 동일한 방식(provider name + NUL + 평문 키 → SHA-256 hex)을 가정한다.</p>
	 *
	 * <p>활성 키와 삭제 예정 키 모두 결과에 포함하여, 호출자가 상태값(ACTIVE / DELETION_REQUESTED)
	 * 을 보고 게이트 처리할 수 있게 한다. 동일 해시로 여러 행이 매칭되면
	 * {@link AmbiguousExternalApiKeyHashException} 으로 409 Conflict 를 유도한다.</p>
	 */
	@Transactional(readOnly = true)
	public InternalApiKeyLookupResponse lookupByHashedKey(ExternalApiKeyProvider provider, String hashedKey) {
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		if (!StringUtils.hasText(hashedKey)) {
			throw new IllegalArgumentException("hashedKey는 필수입니다");
		}
		ExternalApiKeyProvider normalizedProvider = normalizeProvider(provider);
		String normalizedHash = hashedKey.trim().toLowerCase();
		List<ExternalApiKeyEntity> matches =
				externalApiKeyRepository.findAllByProviderAndKeyHash(normalizedProvider, normalizedHash);
		if (matches.isEmpty()) {
			throw new ExternalApiKeyNotFoundException("해당 해시값에 매칭되는 외부 API 키를 찾을 수 없습니다");
		}
		if (matches.size() > 1) {
			log.warn(
					"[AUDIT] external_api_key_lookup_ambiguous provider={} matchCount={} hashPrefix={}",
					normalizedProvider.name(),
					matches.size(),
					normalizedHash.length() >= 8 ? normalizedHash.substring(0, 8) : normalizedHash
			);
			throw new AmbiguousExternalApiKeyHashException(
					"동일한 해시값에 매칭되는 외부 API 키가 2건 이상입니다"
			);
		}
		ExternalApiKeyEntity entity = matches.get(0);
		ExternalApiKeyStatus status = entity.isPendingDeletion()
				? ExternalApiKeyStatus.DELETION_REQUESTED
				: ExternalApiKeyStatus.ACTIVE;
		return new InternalApiKeyLookupResponse(
				String.valueOf(entity.getId()),
				entity.getUserId(),
				status.name(),
				entity.getKeyAlias(),
				InternalApiKeyLookupResponse.SCOPE_USER
		);
	}

	@Transactional(readOnly = true)
	public InternalFingerprintLookupResponse lookupByApiKeyFingerprint(
			ExternalApiKeyProvider provider,
			String fingerprint
	) {
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		if (!StringUtils.hasText(fingerprint)) {
			throw new IllegalArgumentException("fingerprint는 필수입니다");
		}
		ExternalApiKeyProvider normalizedProvider = normalizeProvider(provider);
		String normalizedFingerprint = normalizeFingerprintHex(fingerprint);
		List<ExternalApiKeyEntity> matches = externalApiKeyRepository.findAllByProviderAndApiKeyFingerprint(
				normalizedProvider,
				normalizedFingerprint
		);
		if (matches.isEmpty()) {
			throw new ExternalApiKeyNotFoundException(
					"해당 fingerprint 에 매칭되는 외부 API 키를 찾을 수 없습니다");
		}
		if (matches.size() > 1) {
			log.warn(
					"[AUDIT] external_api_key_fingerprint_lookup_ambiguous provider={} matchCount={} fingerprintPrefix={}",
					normalizedProvider.name(),
					matches.size(),
					normalizedFingerprint.length() >= 8 ? normalizedFingerprint.substring(0, 8) : normalizedFingerprint
			);
			throw new AmbiguousExternalApiKeyHashException(
					"동일한 fingerprint 에 매칭되는 외부 API 키가 2건 이상입니다");
		}
		ExternalApiKeyEntity entity = matches.get(0);
		ExternalApiKeyStatus status = entity.isPendingDeletion()
				? ExternalApiKeyStatus.DELETION_REQUESTED
				: ExternalApiKeyStatus.ACTIVE;
		return InternalFingerprintLookupResponse.personal(
				"u_" + entity.getUserId(),
				String.valueOf(entity.getId()),
				entity.getKeyAlias(),
				status.name(),
				"managed"
		);
	}

	private static String normalizeFingerprintHex(String fingerprint) {
		String trimmed = fingerprint.trim().toLowerCase(Locale.ROOT);
		if (!trimmed.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("fingerprint 는 64자리 소문자 hex 여야 합니다");
		}
		return trimmed;
	}

	@Transactional(readOnly = true)
	public InternalApiKeyResponse resolveInternalKey(
			String userId,
			ExternalApiKeyProvider provider,
			String apiKeyId,
			String alias
	) {
		Long resolvedUserId = resolveInternalLookupUserId(userId);
		if (resolvedUserId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		ExternalApiKeyProvider normalizedProvider = normalizeProvider(provider);
		ExternalApiKeyEntity entity = selectInternalKey(resolvedUserId, normalizedProvider, apiKeyId, alias);
		String plainKey = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey());
		return new InternalApiKeyResponse(plainKey, String.valueOf(entity.getId()));
	}

	/**
	 * 사용자 범위의 활성 외부 API 키만 조회한다. 팀 키로의 폴백은 하지 않는다.
	 *
	 * <p>우선순위: {@code apiKeyId}가 있으면 ID 매칭, 없으면 {@code alias} 최신 건, 둘 다 없으면 해당
	 * provider의 최신 활성 키.</p>
	 */
	private ExternalApiKeyEntity selectInternalKey(
			Long userId,
			ExternalApiKeyProvider provider,
			String apiKeyId,
			String alias
	) {
		if (StringUtils.hasText(apiKeyId)) {
			long keyId;
			try {
				keyId = Long.parseLong(apiKeyId.trim());
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("apiKeyId는 숫자여야 합니다", ex);
			}
			return externalApiKeyRepository
					.findByIdAndUserIdAndProviderAndDeletionRequestedAtIsNull(keyId, userId, provider)
					.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
		}
		if (StringUtils.hasText(alias)) {
			return externalApiKeyRepository
					.findFirstByUserIdAndProviderAndKeyAliasAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
							userId,
							provider,
							alias.trim()
					)
					.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
		}
		return externalApiKeyRepository
				.findTopByUserIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(userId, provider)
				.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
	}

	private Long resolveInternalLookupUserId(String userIdOrEmail) {
		if (!StringUtils.hasText(userIdOrEmail)) {
			return null;
		}
		String normalized = userIdOrEmail.trim();
		if (normalized.contains("@")) {
			String emailKey = normalized.toLowerCase(Locale.ROOT);
			return userRepository.findByEmail(emailKey)
					.map(user -> user.getId())
					.orElse(null);
		}
		try {
			return Long.parseLong(normalized);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	@Transactional(readOnly = true)
	public Optional<BigDecimal> resolveUserMonthlyBudgetUsd(Long userId) {
		return resolveUserMonthlyBudgetBreakdown(userId).map(UserMonthlyBudgetBreakdown::monthlyBudgetUsd);
	}

	@Transactional(readOnly = true)
	public Optional<BigDecimal> resolveUserMonthlyBudgetUsdByEmail(String email) {
		return resolveUserMonthlyBudgetBreakdownByEmail(email).map(UserMonthlyBudgetBreakdown::monthlyBudgetUsd);
	}

	@Transactional(readOnly = true)
	public Optional<UserMonthlyBudgetBreakdown> resolveUserMonthlyBudgetBreakdown(Long userId) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		List<ExternalApiKeyEntity> activeKeys =
				externalApiKeyRepository.findAllByUserIdAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(userId);
		if (activeKeys.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toMonthlyBudgetBreakdown(activeKeys));
	}

	@Transactional(readOnly = true)
	public Optional<UserMonthlyBudgetBreakdown> resolveUserMonthlyBudgetBreakdownByEmail(String email) {
		if (!StringUtils.hasText(email)) {
			throw new IllegalArgumentException("email은 필수입니다");
		}
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		Optional<Long> userId = userRepository.findByEmail(normalizedEmail).map(user -> user.getId());
		if (userId.isEmpty()) {
			return Optional.empty();
		}
		return resolveUserMonthlyBudgetBreakdown(userId.get());
	}

	private UserMonthlyBudgetBreakdown toMonthlyBudgetBreakdown(List<ExternalApiKeyEntity> activeKeys) {
		List<UserMonthlyBudgetByKey> monthlyBudgetsByKey = new ArrayList<>();
		BigDecimal totalBudget = BigDecimal.ZERO;

		for (ExternalApiKeyEntity key : activeKeys) {
			BigDecimal budget = key.getMonthlyBudgetUsd();
			if (budget == null) {
				continue;
			}
			monthlyBudgetsByKey.add(
					new UserMonthlyBudgetByKey(
							key.getId(),
							providerName(key.getProvider()),
							key.getKeyAlias(),
							budget
					)
			);
			totalBudget = totalBudget.add(budget);
		}

		return new UserMonthlyBudgetBreakdown(totalBudget, monthlyBudgetsByKey);
	}

	public record UserMonthlyBudgetBreakdown(
			BigDecimal monthlyBudgetUsd,
			List<UserMonthlyBudgetByKey> monthlyBudgetsByKey
	) {
	}

	public record UserMonthlyBudgetByKey(
			Long externalApiKeyId,
			String provider,
			String alias,
			BigDecimal monthlyBudgetUsd
	) {
	}

	/**
	 * 삭제 요청: 유예 기간 후 {@link #purgeExpiredKeys()} 가 행을 제거한다.
	 */
	@Transactional
	public ExternalApiKeyEntity requestDeletion(Long userId, Long externalKeyId, Integer gracePeriodDays, boolean retainLogs) {
		if (userId == null || externalKeyId == null) {
			throw new IllegalArgumentException("userId와 externalKeyId는 필수입니다");
		}
		ExternalApiKeyEntity entity = externalApiKeyRepository.findByIdAndUserId(externalKeyId, userId)
				.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
		if (entity.isPendingDeletion()) {
			throw new ExternalApiKeyAlreadyPendingDeletionException("이미 삭제 예정인 키입니다");
		}
		int days = resolveGracePeriodDays(gracePeriodDays);
		Instant now = Instant.now();
		if (days == 0) {
			log.info("[AUDIT] external_api_key_immediately_deleted userId={} keyId={}", userId, entity.getId());
			publishExternalApiKeyDeleted(entity, retainLogs);
			externalApiKeyRepository.delete(entity);
			return entity;
		}
		entity.markPendingDeletion(now, days, retainLogs);
		log.info(
				"[AUDIT] external_api_key_deletion_requested userId={} keyId={} permanentDeletionAt={}",
				userId,
				entity.getId(),
				entity.getPermanentDeletionAt()
		);
		publishExternalApiKeyStatusChanged(entity, ExternalApiKeyStatus.DELETION_REQUESTED);
		publishExternalApiKeyBudgetChanged(entity, ExternalApiKeyStatus.DELETION_REQUESTED);
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
		publishExternalApiKeyStatusChanged(entity, ExternalApiKeyStatus.ACTIVE);
		publishExternalApiKeyBudgetChanged(entity, ExternalApiKeyStatus.ACTIVE);
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
					providerName(e.getProvider())
			);
			publishExternalApiKeyDeleted(e, e.isRetainUsageLogs());
		}
		externalApiKeyRepository.deleteAll(expired);
		return expired.size();
	}

	private void publishExternalApiKeyStatusChanged(ExternalApiKeyEntity entity, ExternalApiKeyStatus status) {
		ExternalApiKeyStatusChangedEvent event = ExternalApiKeyStatusChangedEvent.of(
				entity.getId(),
				entity.getKeyAlias(),
				principalSubForUser(entity.getUserId()),
				providerName(entity.getProvider()),
				status,
				entity.getKeyHash()
		);
		applicationEventPublisher.publishEvent(event);
	}

	private void publishExternalApiKeyDeleted(ExternalApiKeyEntity entity, boolean retainLogs) {
		ExternalApiKeyDeletedEvent event = ExternalApiKeyDeletedEvent.of(
				principalSubForUser(entity.getUserId()),
				entity.getId(),
				Instant.now(),
				retainLogs,
				providerName(entity.getProvider()),
				entity.getKeyAlias()
		);
		applicationEventPublisher.publishEvent(event);
	}

	private void publishExternalApiKeyBudgetChanged(ExternalApiKeyEntity entity, ExternalApiKeyStatus status) {
		ExternalApiKeyBudgetChangedEvent event = ExternalApiKeyBudgetChangedEvent.of(
				entity.getId(),
				entity.getKeyAlias(),
				principalSubForUser(entity.getUserId()),
				providerName(entity.getProvider()),
				status,
				entity.getMonthlyBudgetUsd(),
				entity.getKeyHash()
		);
		applicationEventPublisher.publishEvent(event);
	}

	private String principalSubForUser(Long userId) {
		return userRepository.findById(userId)
				.map(User::getEmail)
				.map(email -> email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new IllegalStateException("user not found for external API key owner userId=" + userId));
	}

	private static ExternalApiKeyProvider normalizeProvider(ExternalApiKeyProvider provider) {
		return provider;
	}

	private static String providerName(ExternalApiKeyProvider provider) {
		return normalizeProvider(provider).name();
	}

	private static int resolveGracePeriodDays(Integer requested) {
		int days = requested != null ? requested : DEFAULT_GRACE_DAYS;
		if (days < MIN_GRACE_DAYS || days > MAX_GRACE_DAYS) {
			throw new IllegalArgumentException(
					"유예 기간은 " + MIN_GRACE_DAYS + "일 이상 " + MAX_GRACE_DAYS + "일 이하로 설정할 수 있습니다"
			);
		}
		return days;
	}

	private RuntimeException toDuplicateException(
			Long userId,
			ExternalApiKeyProvider provider,
			String alias,
			String keyHash,
			DataIntegrityViolationException ex
	) {
		if (externalApiKeyRepository.existsByUserIdAndKeyAlias(userId, alias)) {
			return new DuplicateExternalApiKeyAliasException("이미 사용 중인 별칭입니다");
		}
		Optional<ExternalApiKeyEntity> existingSameHash =
				externalApiKeyRepository.findByUserIdAndProviderAndKeyHash(userId, provider, keyHash);
		if (existingSameHash.isPresent()) {
			if (existingSameHash.get().isPendingDeletion()) {
				return new DuplicateExternalApiKeyException("삭제예정키와 중복된 키");
			}
			return new DuplicateExternalApiKeyException("이미 등록된 API 키입니다");
		}
		return ex;
	}

	private void verifyNoTeamScopeDuplicate(ExternalApiKeyProvider provider, String apiKeyFingerprint) {
		if (teamApiKeyLookupClient.existsByRawKeyFingerprint(provider.name(), apiKeyFingerprint)) {
			throw new DuplicateExternalApiKeyException("팀에 이미 등록된 API 키입니다");
		}
		if (provider == ExternalApiKeyProvider.ANTHROPIC) {
			if (teamApiKeyLookupClient.existsByRawKeyFingerprint("CLAUDE", apiKeyFingerprint)) {
				throw new DuplicateExternalApiKeyException("팀에 이미 등록된 API 키입니다");
			}
		}
	}

	@Transactional(readOnly = true)
	public List<InternalApiKeyHashEntry> listKeyHashesForInternal(Long userId) {
		if (userId == null || userId <= 0) {
			return List.of();
		}
		return externalApiKeyRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(e -> new InternalApiKeyHashEntry(e.getId(), e.getKeyHash()))
				.toList();
	}
}
