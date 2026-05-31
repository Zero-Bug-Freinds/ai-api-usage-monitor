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
 * 탈퇴 직후 {@link #purgeUserIdentityImmediately(Long)} 로 사용자 행을 제거하고,
 * billing·usage·team ACK 완료 후 {@link #finalizePendingAfterAllAcks(Long)} 로 pending 만 정리한다.
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

	/**
	 * 탈퇴 API 성공 직후 호출. {@code account_deletion_pending} 는 ACK 수집용으로 유지한다.
	 */
	@Transactional
	public void purgeUserIdentityImmediately(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("User already removed or missing"));
		passwordResetTokenRepository.deleteByUser(user);
		externalApiKeyRepository.deleteAllByUserId(userId);
		userRepository.delete(user);
	}

	/**
	 * 연동 서비스 ACK 가 모두 도착한 뒤 pending 행만 제거한다. 사용자 행은 이미 삭제된 상태여야 한다.
	 */
	@Transactional
	public void finalizePendingAfterAllAcks(Long userId) {
		accountDeletionPendingRepository.deleteById(userId);
	}
}
