package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.TokenResponse;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.entity.RefreshTokenEntity;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import com.zerobugfreinds.identity_service.repository.RefreshTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 사용자 도메인 유스케이스.
 */
@Service
public class UserService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final TeamMembershipVerificationClient teamMembershipVerificationClient;

	public UserService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider,
			TeamMembershipVerificationClient teamMembershipVerificationClient
	) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.teamMembershipVerificationClient = teamMembershipVerificationClient;
	}

	/**
	 * 회원가입: 이메일 중복 검사 후 BCrypt 로 비밀번호를 암호화하여 저장한다.
	 */
	@Transactional
	public SignupResponse signup(SignupRequest request) {
		validateSignupRequest(request);

		if (userRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException("이미 사용 중인 이메일입니다");
		}
		String encodedPassword = passwordEncoder.encode(request.password());
		User user = new User(
				request.email(),
				encodedPassword,
				request.name(),
				request.role()
		);
		User saved = userRepository.save(user);
		return new SignupResponse(saved.getId(), saved.getEmail(), saved.getName(), saved.getRole());
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
		User user = userRepository.findByEmail(request.email())
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
		boolean isValidMember = teamMembershipVerificationClient.isActiveTeamMember(targetTeamId, authenticatedUserId);
		if (!isValidMember) {
			throw new IllegalArgumentException("요청한 팀의 활성 멤버가 아닙니다");
		}
		User user = userRepository.findById(authenticatedUserId)
				.orElseThrow(() -> new InvalidCredentialsException("사용자 정보를 찾을 수 없습니다"));
		return issueTokenPair(user, targetTeamId);
	}

	@Transactional(readOnly = true)
	public boolean existsByEmail(String email) {
		if (email == null || email.isBlank()) {
			return false;
		}
		return userRepository.existsByEmail(email.trim());
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

	private TokenResponse issueTokenPair(User user, Long activeTeamId) {
		String accessToken = jwtTokenProvider.createAccessToken(user, activeTeamId);
		String refreshToken = jwtTokenProvider.createRefreshToken(user, activeTeamId);
		replaceRefreshToken(user.getId(), refreshToken, activeTeamId);
		return new TokenResponse(
				accessToken,
				refreshToken,
				"Bearer",
				jwtTokenProvider.getAccessTokenTtlSeconds(),
				jwtTokenProvider.getRefreshTokenTtlSeconds()
		);
	}

	private void replaceRefreshToken(Long userId, String refreshToken, Long activeTeamId) {
		refreshTokenRepository.deleteAllByUserId(userId);
		RefreshTokenEntity tokenEntity = RefreshTokenEntity.issue(
				userId,
				sha256Hex(refreshToken),
				activeTeamId,
				Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenTtlSeconds())
		);
		refreshTokenRepository.save(tokenEntity);
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
