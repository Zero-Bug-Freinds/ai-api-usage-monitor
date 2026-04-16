package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.LoginResponse;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 사용자 도메인 유스케이스.
 */
@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	public UserService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
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
	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
		}

		String accessToken = jwtTokenProvider.generateAccessToken(user);
		return new LoginResponse(accessToken, "Bearer", jwtTokenProvider.getAccessTokenTtlSeconds());
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
}
