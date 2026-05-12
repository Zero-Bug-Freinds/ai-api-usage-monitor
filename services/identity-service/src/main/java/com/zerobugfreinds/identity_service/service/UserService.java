package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.InternalUserPrincipalResponse;
import com.zerobugfreinds.identity_service.dto.ProfileUpdateResponse;
import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.dto.UpdateProfileRequest;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.TokenResponse;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import com.zerobugfreinds.identity_service.repository.RefreshTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import com.zerobugfreinds.identity_service.mq.IdentityUserSyncEventPublisher;
import com.zerobugfreinds.identity.events.IdentityUserSyncEvent;
import com.zerobugfreinds.identity.events.IdentityUserSyncEventTypes;
import com.zerobugfreinds.identity.events.UserContextChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 사용자 도메인 유스케이스.
 */
@Service
public class UserService {
	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final TeamMembershipVerificationClient teamMembershipVerificationClient;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final IdentityUserSyncEventPublisher identityUserSyncEventPublisher;

	public UserService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider,
			TeamMembershipVerificationClient teamMembershipVerificationClient,
			ApplicationEventPublisher applicationEventPublisher,
			IdentityUserSyncEventPublisher identityUserSyncEventPublisher
	) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.teamMembershipVerificationClient = teamMembershipVerificationClient;
		this.applicationEventPublisher = applicationEventPublisher;
		this.identityUserSyncEventPublisher = identityUserSyncEventPublisher;
	}

	/**
	 * 회원가입: 이메일 중복 검사 후 BCrypt 로 비밀번호를 암호화하여 저장한다.
	 */
	@Transactional
	public SignupResponse signup(SignupRequest request) {
		validateSignupRequest(request);
		String normalizedEmail = normalizeEmail(request.email());

		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new DuplicateEmailException("이미 사용 중인 이메일입니다");
		}
		String encodedPassword = passwordEncoder.encode(request.password());
		User user = new User(
				normalizedEmail,
				encodedPassword,
				request.name(),
				request.role()
		);
		User saved;
		try {
			saved = userRepository.save(user);
		} catch (DataIntegrityViolationException ex) {
			throw new DuplicateEmailException("이미 사용 중인 이메일입니다");
		}
		identityUserSyncEventPublisher.publishAfterCommit(
				IdentityUserSyncEvent.of(
						IdentityUserSyncEventTypes.USER_REGISTERED,
						saved.getId(),
						saved.getEmail(),
						saved.getName(),
						Instant.now()
				)
		);
		return new SignupResponse(saved.getId(), saved.getEmail(), saved.getName(), saved.getRole());
	}

	/**
	 * 이메일·표시 이름 갱신 후 team-service 동기화용 {@code USER_PROFILE_UPDATED} 를 커밋 이후 발행한다.
	 */
	@Transactional
	public ProfileUpdateResponse updateProfile(Long userId, UpdateProfileRequest request) {
		String rawEmail = request.email() != null ? request.email().trim() : "";
		String rawName = request.name() != null ? request.name().trim() : "";
		if (rawEmail.isEmpty() && rawName.isEmpty()) {
			throw new InvalidSignupRequestException("변경할 이메일 또는 이름을 입력해 주세요");
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new InvalidCredentialsException("사용자 정보를 찾을 수 없습니다"));

		boolean changed = false;
		if (!rawEmail.isEmpty()) {
			String normalizedEmail = normalizeEmail(rawEmail);
			assertValidProfileEmail(normalizedEmail);
			if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
				userRepository.findByEmailIgnoreCase(normalizedEmail)
						.filter(other -> !other.getId().equals(userId))
						.ifPresent(other -> {
							throw new DuplicateEmailException("이미 사용 중인 이메일입니다");
						});
				user.setEmail(normalizedEmail);
				changed = true;
			}
		}
		if (!rawName.isEmpty() && !rawName.equals(user.getName())) {
			user.setName(rawName);
			changed = true;
		}

		if (changed) {
			try {
				userRepository.save(user);
			} catch (DataIntegrityViolationException ex) {
				throw new DuplicateEmailException("이미 사용 중인 이메일입니다");
			}
			identityUserSyncEventPublisher.publishAfterCommit(
					IdentityUserSyncEvent.of(
							IdentityUserSyncEventTypes.USER_PROFILE_UPDATED,
							user.getId(),
							user.getEmail(),
							user.getName(),
							Instant.now()
					)
			);
		}

		return new ProfileUpdateResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
	}

	private static void assertValidProfileEmail(String normalizedEmail) {
		try {
			InternetAddress addr = new InternetAddress(normalizedEmail, false);
			addr.validate();
		} catch (AddressException ex) {
			throw new InvalidSignupRequestException("유효한 이메일 형식이 아닙니다");
		}
	}

	private void validateSignupRequest(SignupRequest request) {
		if (!request.password().equals(request.passwordConfirm())) {
			throw new InvalidSignupRequestException("비밀번호와 비밀번호 확인이 일치하지 않습니다");
		}
	}

	/**
	 * 로그인: 이메일/비밀번호를 검증하고 JWT 액세스 토큰을 발행한다.
	 */
	@Transactional
	public TokenResponse login(LoginRequest request) {
		String normalizedEmail = normalizeEmail(request.email());
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
				.orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
		}

		return issueTokenPair(user, null);
	}

	@Transactional
	public TokenResponse switchTeam(Long authenticatedUserId, Long targetTeamId) {
		if (authenticatedUserId == null) {
			throw new IllegalArgumentException("인증 사용자 정보가 없습니다");
		}
		if (targetTeamId == null) {
			throw new IllegalArgumentException("targetTeamId는 필수입니다");
		}
		User user = userRepository.findById(authenticatedUserId)
				.orElseThrow(() -> new InvalidCredentialsException("사용자 정보를 찾을 수 없습니다"));
		boolean matchedByUserId = teamMembershipVerificationClient.isActiveTeamMember(targetTeamId, authenticatedUserId);
		boolean matchedByEmail = false;
		if (!matchedByUserId && user.getEmail() != null && !user.getEmail().isBlank()) {
			matchedByEmail = teamMembershipVerificationClient.isActiveTeamMember(targetTeamId, user.getEmail());
		}
		boolean isValidMember = matchedByUserId || matchedByEmail;
		log.info(
				"switchTeam membership check userId={} email={} teamId={} matchedByUserId={} matchedByEmail={}",
				authenticatedUserId,
				user.getEmail(),
				targetTeamId,
				matchedByUserId,
				matchedByEmail
		);
		if (!isValidMember) {
			throw new IllegalArgumentException("요청한 팀의 활성 멤버가 아닙니다");
		}
		return issueTokenPair(user, targetTeamId);
	}

	@Transactional(readOnly = true)
	public boolean existsByEmail(String email) {
		if (email == null || email.isBlank()) {
			return false;
		}
		return userRepository.existsByEmailIgnoreCase(normalizeEmail(email));
	}

	/**
	 * 내부 서비스(team 프록시 멤버십 등)용: 이메일 또는 숫자 user id 문자열로 동일 사용자의 id·이메일을 조회한다.
	 */
	@Transactional(readOnly = true)
	public Optional<InternalUserPrincipalResponse> resolvePrincipalForInternalLookup(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String t = raw.trim();
		if (t.contains("@")) {
			return userRepository.findByEmailIgnoreCase(normalizeEmail(t))
					.map(u -> new InternalUserPrincipalResponse(String.valueOf(u.getId()), normalizeEmail(u.getEmail())));
		}
		try {
			Long id = Long.parseLong(t);
			return userRepository.findById(id)
					.map(u -> new InternalUserPrincipalResponse(String.valueOf(u.getId()), normalizeEmail(u.getEmail())));
		} catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

	@Transactional(readOnly = true)
	public Set<String> findExistingUserIds(List<String> rawUserIds) {
		if (rawUserIds == null || rawUserIds.isEmpty()) {
			return Set.of();
		}
		Set<Long> numericUserIds = new LinkedHashSet<>();
		for (String rawUserId : rawUserIds) {
			if (rawUserId == null || rawUserId.isBlank()) {
				continue;
			}
			try {
				numericUserIds.add(Long.parseLong(rawUserId.trim()));
			} catch (NumberFormatException ignored) {
				// team-service userId 는 문자열이므로 숫자 변환 실패 값은 미존재 사용자로 취급한다.
			}
		}
		if (numericUserIds.isEmpty()) {
			return Set.of();
		}
		return userRepository.findAllById(numericUserIds).stream()
				.map(user -> String.valueOf(user.getId()))
				.collect(LinkedHashSet::new, Set::add, Set::addAll);
	}

	@Transactional(readOnly = true)
	public Optional<String> findEmailByUserId(String rawUserId) {
		if (rawUserId == null || rawUserId.isBlank()) {
			return Optional.empty();
		}
		try {
			Long userId = Long.parseLong(rawUserId.trim());
			return userRepository.findById(userId).map(User::getEmail);
		} catch (NumberFormatException ignored) {
			return Optional.empty();
		}
	}

	@Transactional(readOnly = true)
	public User findByAuthenticatedPrincipal(String principal) {
		if (principal == null || principal.isBlank()) {
			throw new InvalidCredentialsException("인증 사용자 정보가 없습니다");
		}
		String normalized = principal.trim();
		try {
			Long userId = Long.parseLong(normalized);
			return userRepository.findById(userId)
					.orElseThrow(() -> new InvalidCredentialsException("사용자 정보를 찾을 수 없습니다"));
		} catch (NumberFormatException ignored) {
			return userRepository.findByEmailIgnoreCase(normalizeEmail(normalized))
					.orElseThrow(() -> new InvalidCredentialsException("사용자 정보를 찾을 수 없습니다"));
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private TokenResponse issueTokenPair(User user, Long activeTeamId) {
		boolean firstSession = !refreshTokenRepository.existsByUserId(user.getId());
		String accessToken = jwtTokenProvider.createAccessToken(user, activeTeamId);
		String refreshToken = jwtTokenProvider.createRefreshToken(user, activeTeamId);
		replaceRefreshToken(user.getId(), refreshToken, activeTeamId);
		applicationEventPublisher.publishEvent(
				UserContextChangedEvent.of(user.getId(), activeTeamId, user.getRole().name())
		);
		if (firstSession) {
			identityUserSyncEventPublisher.publishAfterCommit(
					IdentityUserSyncEvent.of(
							IdentityUserSyncEventTypes.USER_FIRST_LOGIN_RECORDED,
							user.getId(),
							user.getEmail(),
							user.getName(),
							Instant.now()
					)
			);
		}
		return new TokenResponse(
				accessToken,
				refreshToken,
				"Bearer",
				jwtTokenProvider.getAccessTokenTtlSeconds(),
				jwtTokenProvider.getRefreshTokenTtlSeconds()
		);
	}

	private void replaceRefreshToken(Long userId, String refreshToken, Long activeTeamId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(jwtTokenProvider.getRefreshTokenTtlSeconds());
		refreshTokenRepository.upsertByUserId(userId, sha256Hex(refreshToken), activeTeamId, expiresAt, now);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
		}
	}
}
