package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.mq.UserAccountDeletionEventPublisher;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴: 비밀번호 검증 후 대기 행을 두고 삭제 요청 이벤트를 보낸다.
 * 연동 서비스 ACK 후 {@link AccountDeletionCoordinationService}가 pending 행만 정리한다.
 */
@Service
public class AccountDeletionService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountDeletionCoordinationService accountDeletionCoordinationService;
	private final IdentityAccountLocalDeletionService identityAccountLocalDeletionService;
	private final UserAccountDeletionEventPublisher userAccountDeletionEventPublisher;
	private final RefreshTokenRevocationService refreshTokenRevocationService;

	public AccountDeletionService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			AccountDeletionCoordinationService accountDeletionCoordinationService,
			IdentityAccountLocalDeletionService identityAccountLocalDeletionService,
			UserAccountDeletionEventPublisher userAccountDeletionEventPublisher,
			RefreshTokenRevocationService refreshTokenRevocationService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.accountDeletionCoordinationService = accountDeletionCoordinationService;
		this.identityAccountLocalDeletionService = identityAccountLocalDeletionService;
		this.userAccountDeletionEventPublisher = userAccountDeletionEventPublisher;
		this.refreshTokenRevocationService = refreshTokenRevocationService;
	}

	/**
	 * 비밀번호 검증 → pending 등록 → 세션 무효화 → 연동 정리 이벤트 발행 → identity 사용자 행 즉시 삭제.
	 * 재로그인·재가입은 미가입자와 동일하게 동작한다(동일 이메일 재가입 가능). pending 은 ACK 용도만 남긴다.
	 */
	public void deleteAuthenticatedAccount(Long userId, String rawPassword) {
		if (userId == null) {
			throw new IllegalArgumentException("인증 사용자 정보가 없습니다");
		}
		if (rawPassword == null || rawPassword.isBlank()) {
			throw new InvalidCredentialsException("Password is required");
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));
		if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
			throw new InvalidCredentialsException("Invalid password");
		}

		Long identityUserId = user.getId();
		String userEmail = user.getEmail();

		accountDeletionCoordinationService.registerDeletionRequested(user);
		refreshTokenRevocationService.revokeAllForAccountDeletion(identityUserId);
		userAccountDeletionEventPublisher.publish(
				UserAccountDeletionRequestedEvent.of(identityUserId, userEmail)
		);
		identityAccountLocalDeletionService.purgeUserIdentityImmediately(identityUserId);
	}
}
