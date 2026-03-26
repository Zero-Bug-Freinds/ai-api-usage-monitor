package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.LoginResponse;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
