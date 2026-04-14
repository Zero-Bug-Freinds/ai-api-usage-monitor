package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.repository.AccountDeletionPendingRepository;
import com.zerobugfreinds.identity_service.repository.ExternalApiKeyRepository;
import com.zerobugfreinds.identity_service.repository.PasswordResetTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity DB만 다룬다. 타 서비스 DB에 접근하지 않는다.
 * {@link AccountDeletionCoordinationService}가 billing·usage·team ACK 를 모은 뒤 호출한다.
 */
@Service
public class IdentityAccountLocalDeletionService {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final ExternalApiKeyRepository externalApiKeyRepository;
	private final AccountDeletionPendingRepository accountDeletionPendingRepository;

	public IdentityAccountLocalDeletionService(
			UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			ExternalApiKeyRepository externalApiKeyRepository,
			AccountDeletionPendingRepository accountDeletionPendingRepository
	) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.externalApiKeyRepository = externalApiKeyRepository;
		this.accountDeletionPendingRepository = accountDeletionPendingRepository;
	}

	@Transactional
	public void deleteAllDataForUser(Long userId) {
		accountDeletionPendingRepository.deleteById(userId);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("User already removed or missing"));
		passwordResetTokenRepository.deleteByUser(user);
		externalApiKeyRepository.deleteAllByUserId(userId);
		userRepository.delete(user);
	}
}
