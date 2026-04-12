package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.ForgotPasswordRequest;
import com.zerobugfreinds.identity_service.dto.ResetPasswordRequest;
import com.zerobugfreinds.identity_service.entity.PasswordResetToken;
import com.zerobugfreinds.identity_service.entity.Role;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.InvalidPasswordResetTokenException;
import com.zerobugfreinds.identity_service.repository.PasswordResetTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private EncryptionUtil encryptionUtil;
	@Mock
	private PasswordResetMailService passwordResetMailService;

	private PasswordResetService passwordResetService;

	@BeforeEach
	void setUp() {
		passwordResetService = new PasswordResetService(
				userRepository,
				passwordResetTokenRepository,
				passwordEncoder,
				encryptionUtil,
				passwordResetMailService,
				1
		);
	}

	@Test
	void requestForgotPassword_doesNothingWhenEmailUnknown() {
		when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());

		passwordResetService.requestForgotPassword(new ForgotPasswordRequest("a@b.com"));

		verify(passwordResetTokenRepository, never()).save(any());
		verify(passwordResetMailService, never()).sendResetLink(any(), any());
	}

	@Test
	void requestForgotPassword_savesTokenAndSendsMailWhenUserExists() {
		User user = new User("a@b.com", "hash", "n", Role.USER);
		when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
		when(encryptionUtil.sha256HexUtf8(any())).thenReturn("deadbeef");

		passwordResetService.requestForgotPassword(new ForgotPasswordRequest("a@b.com"));

		verify(passwordResetTokenRepository).deleteByUser(user);
		verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
		verify(passwordResetMailService).sendResetLink(eq("a@b.com"), any());
	}

	@Test
	void resetPassword_throwsWhenTokenUnknown() {
		when(encryptionUtil.sha256HexUtf8("tok")).thenReturn("hh");
		when(passwordResetTokenRepository.findByTokenHash("hh")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> passwordResetService.resetPassword(
				new ResetPasswordRequest("tok", "abc123!@", "abc123!@")
		)).isInstanceOf(InvalidPasswordResetTokenException.class);
	}

	@Test
	void resetPassword_throwsWhenTokenExpired() {
		User user = new User("a@b.com", "hash", "n", Role.USER);
		PasswordResetToken token = new PasswordResetToken(user, "hh", Instant.now().minusSeconds(3600));
		when(encryptionUtil.sha256HexUtf8("tok")).thenReturn("hh");
		when(passwordResetTokenRepository.findByTokenHash("hh")).thenReturn(Optional.of(token));

		assertThatThrownBy(() -> passwordResetService.resetPassword(
				new ResetPasswordRequest("tok", "abc123!@", "abc123!@")
		)).isInstanceOf(InvalidPasswordResetTokenException.class);
	}

	@Test
	void resetPassword_updatesUserPassword() {
		User user = new User("a@b.com", "oldhash", "n", Role.USER);
		PasswordResetToken token = new PasswordResetToken(user, "hh", Instant.now().plusSeconds(3600));
		when(encryptionUtil.sha256HexUtf8("tok")).thenReturn("hh");
		when(passwordResetTokenRepository.findByTokenHash("hh")).thenReturn(Optional.of(token));
		when(passwordEncoder.encode("abc123!@")).thenReturn("newhash");

		passwordResetService.resetPassword(new ResetPasswordRequest("tok", "abc123!@", "abc123!@"));

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		org.assertj.core.api.Assertions.assertThat(userCaptor.getValue().getPassword()).isEqualTo("newhash");

		ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(passwordResetTokenRepository).save(tokenCaptor.capture());
		org.assertj.core.api.Assertions.assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();
	}
}
