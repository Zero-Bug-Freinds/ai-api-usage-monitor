package com.zerobugfreinds.identity_service.repository;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 외부 API 키 영속성.
 */
public interface ExternalApiKeyRepository extends JpaRepository<ExternalApiKeyEntity, Long> {

	boolean existsByUserIdAndProviderAndKeyHash(Long userId, ExternalApiKeyProvider provider, String keyHash);

	long countByUserId(Long userId);

	java.util.List<ExternalApiKeyEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	java.util.Optional<ExternalApiKeyEntity> findTopByUserIdAndProviderOrderByCreatedAtDesc(
			Long userId,
			ExternalApiKeyProvider provider
	);
}
