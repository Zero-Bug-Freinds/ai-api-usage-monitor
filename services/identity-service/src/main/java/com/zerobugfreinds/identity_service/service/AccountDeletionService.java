package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.mq.UserAccountDeletionEventPublisher;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.IdentityUserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴: 비밀번호 검증 후 대기 행을 두고 삭제 요청 이벤트를 보낸다.
 * billing·usage·team 이 ACK 를 보낸 뒤 {@link AccountDeletionCoordinationService}가 identity 로컬을 정리한다.
 */
@Service
public class AccountDeletionService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountDeletionCoordinationService accountDeletionCoordinationService;
	private final UserAccountDeletionEventPublisher userAccountDeletionEventPublisher;

	public AccountDeletionService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			AccountDeletionCoordinationService accountDeletionCoordinationService,
			UserAccountDeletionEventPublisher userAccountDeletionEventPublisher
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.accountDeletionCoordinationService = accountDeletionCoordinationService;
		this.userAccountDeletionEventPublisher = userAccountDeletionEventPublisher;
	}

	/**
	 * 비밀번호 검증 → 탈퇴 대기 등록 → 삭제 요청 이벤트 발행 (로컬 사용자 행은 ACK 후 삭제).
	 */
	public void deleteAuthenticatedAccount(IdentityUserPrincipal principal, String rawPassword) {
		if (rawPassword == null || rawPassword.isBlank()) {
			throw new InvalidCredentialsException("Password is required");
		}
		User user = userRepository.findById(principal.userId())
				.orElseThrow(() -> new IllegalArgumentException("User not found"));
		if (!user.getEmail().equals(principal.email())) {
			throw new IllegalArgumentException("Session does not match user record");
		}
		if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
			throw new InvalidCredentialsException("Invalid password");
		}

		accountDeletionCoordinationService.registerDeletionRequested(user);
		userAccountDeletionEventPublisher.publish(
				UserAccountDeletionRequestedEvent.of(user.getId(), user.getEmail())
		);
	}
}
