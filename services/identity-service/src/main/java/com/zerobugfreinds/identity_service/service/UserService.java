package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.repository.UserRepository;
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

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
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
}
