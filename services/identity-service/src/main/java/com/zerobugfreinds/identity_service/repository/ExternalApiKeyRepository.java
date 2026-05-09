package com.zerobugfreinds.identity_service.repository;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 외부 API 키 영속성.
 */
public interface ExternalApiKeyRepository extends JpaRepository<ExternalApiKeyEntity, Long> {

	boolean existsByUserIdAndProviderAndKeyHashAndDeletionRequestedAtIsNull(
			Long userId,
			ExternalApiKeyProvider provider,
			String keyHash
	);

	long countByUserIdAndProviderAndKeyHashAndDeletionRequestedAtIsNull(
			Long userId,
			ExternalApiKeyProvider provider,
			String keyHash
	);

	Optional<ExternalApiKeyEntity> findByUserIdAndProviderAndKeyHash(
			Long userId,
			ExternalApiKeyProvider provider,
			String keyHash
	);

	/** 별칭은 삭제 예정 행까지 포함해 사용자당 유일(유예 중에도 동일 별칭으로 새 등록 불가). */
	boolean existsByUserIdAndKeyAlias(Long userId, String keyAlias);
	boolean existsByUserIdAndKeyAliasAndIdNot(Long userId, String keyAlias, Long id);

	boolean existsByUserIdAndProviderAndKeyHashAndIdNotAndDeletionRequestedAtIsNull(
			Long userId,
			ExternalApiKeyProvider provider,
			String keyHash,
			Long id
	);

	long countByUserId(Long userId);

	long countByUserIdAndDeletionRequestedAtIsNull(Long userId);

	long countByUserIdAndDeletionRequestedAtIsNullAndMonthlyBudgetUsdIsNotNull(Long userId);

	List<ExternalApiKeyEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	List<ExternalApiKeyEntity> findAllByUserIdAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(Long userId);

	Optional<ExternalApiKeyEntity> findTopByUserIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
			Long userId,
			ExternalApiKeyProvider provider
	);

	@Query("""
			select coalesce(sum(e.monthlyBudgetUsd), 0)
			from ExternalApiKeyEntity e
			where e.userId = :userId
			  and e.deletionRequestedAt is null
			""")
	java.math.BigDecimal sumMonthlyBudgetUsdByUserIdAndDeletionRequestedAtIsNull(@Param("userId") Long userId);

	@Query("""
			select sum(e.monthlyBudgetUsd)
			from ExternalApiKeyEntity e
			where e.userId = :userId
			  and e.deletionRequestedAt is null
			  and e.monthlyBudgetUsd is not null
			""")
	java.math.BigDecimal sumMonthlyBudgetUsdByUserIdAndActiveBudgetAssigned(@Param("userId") Long userId);

	Optional<ExternalApiKeyEntity> findByIdAndUserId(Long id, Long userId);

	Optional<ExternalApiKeyEntity> findByIdAndUserIdAndDeletionRequestedAtIsNull(Long id, Long userId);

	List<ExternalApiKeyEntity> findAllByPermanentDeletionAtIsNotNullAndPermanentDeletionAtBefore(Instant now);

	/**
	 * Proxy 등 내부 호출에서 사용자 ID 없이 (provider, keyHash) 만으로 역조회할 때 사용한다.
	 * 유일 제약은 (user_id, provider, key_hash) 이므로 이론상 여러 사용자가 동일한 외부 키를
	 * 등록한 경우 2건 이상 조회될 수 있다. 호출자에서 결과 건수를 검증한다.
	 */
	List<ExternalApiKeyEntity> findAllByProviderAndKeyHash(ExternalApiKeyProvider provider, String keyHash);

	void deleteAllByUserId(Long userId);
}
