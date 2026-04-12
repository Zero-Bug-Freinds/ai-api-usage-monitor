package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.ForgotPasswordRequest;
import com.zerobugfreinds.identity_service.dto.ResetPasswordRequest;
import com.zerobugfreinds.identity_service.entity.PasswordResetToken;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.InvalidPasswordResetTokenException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import com.zerobugfreinds.identity_service.repository.PasswordResetTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 비밀번호 찾기·재설정 유스케이스.
 */
@Service
public class PasswordResetService {

	public static final String FORGOT_PASSWORD_UNIFORM_MESSAGE =
			"등록된 이메일이면 비밀번호 재설정 안내를 보냈습니다";

	private static final int RAW_TOKEN_BYTES = 32;

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final EncryptionUtil encryptionUtil;
	private final PasswordResetMailService passwordResetMailService;
	private final SecureRandom secureRandom = new SecureRandom();
	private final int tokenValidityHours;

	public PasswordResetService(
			UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordEncoder passwordEncoder,
			EncryptionUtil encryptionUtil,
			PasswordResetMailService passwordResetMailService,
			@Value("${identity.passwordReset.tokenValidityHours}") int tokenValidityHours
	) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.encryptionUtil = encryptionUtil;
		this.passwordResetMailService = passwordResetMailService;
		this.tokenValidityHours = tokenValidityHours;
	}

	/**
	 * 이메일이 존재하면 재설정 토큰을 발급하고 메일을 보낸다. 존재 여부는 응답으로 드러내지 않는다.
	 */
	@Transactional
	public void requestForgotPassword(ForgotPasswordRequest request) {
		String email = request.email().trim();
		Optional<User> userOpt = userRepository.findByEmail(email);
		if (userOpt.isEmpty()) {
			return;
		}
		User user = userOpt.get();
		passwordResetTokenRepository.deleteByUser(user);

		String rawToken = randomTokenHex(RAW_TOKEN_BYTES);
		String tokenHash = encryptionUtil.sha256HexUtf8(rawToken);
		Instant expiresAt = Instant.now().plus(tokenValidityHours, ChronoUnit.HOURS);
		passwordResetTokenRepository.save(new PasswordResetToken(user, tokenHash, expiresAt));

		passwordResetMailService.sendResetLink(user.getEmail(), rawToken);
	}

	/**
	 * 토큰으로 비밀번호를 재설정한다. 토큰은 일회용이며 사용 후 무효화된다.
	 */
	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		if (!request.password().equals(request.passwordConfirm())) {
			throw new InvalidSignupRequestException("비밀번호와 비밀번호 확인이 일치하지 않습니다");
		}
		String tokenHash = encryptionUtil.sha256HexUtf8(request.token().trim());
		PasswordResetToken entity = passwordResetTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new InvalidPasswordResetTokenException(
						"링크가 만료되었거나 유효하지 않습니다"));

		if (entity.getUsedAt() != null) {
			throw new InvalidPasswordResetTokenException("링크가 만료되었거나 유효하지 않습니다");
		}
		if (entity.getExpiresAt().isBefore(Instant.now())) {
			throw new InvalidPasswordResetTokenException("링크가 만료되었거나 유효하지 않습니다");
		}

		User user = entity.getUser();
		user.setPassword(passwordEncoder.encode(request.password()));
		userRepository.save(user);

		entity.markUsed();
		passwordResetTokenRepository.save(entity);
	}

	private String randomTokenHex(int numBytes) {
		byte[] bytes = new byte[numBytes];
		secureRandom.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
