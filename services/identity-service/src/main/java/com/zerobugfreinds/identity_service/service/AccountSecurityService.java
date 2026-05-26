package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.ChangePasswordRequest;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인된 사용자의 보안 설정(현재는 비밀번호 변경)을 담당한다.
 */
@Service
public class AccountSecurityService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AccountSecurityService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest request) {
		if (userId == null) {
			throw new InvalidCredentialsException("인증 사용자 정보가 없습니다");
		}
		if (!request.newPassword().equals(request.newPasswordConfirm())) {
			throw new InvalidSignupRequestException("비밀번호와 비밀번호 확인이 일치하지 않습니다");
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new InvalidCredentialsException("사용자 정보를 찾을 수 없습니다"));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new InvalidCredentialsException("현재 비밀번호가 올바르지 않습니다");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
			throw new InvalidSignupRequestException("새 비밀번호가 현재 비밀번호와 같습니다");
		}
		user.setPassword(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}
}
