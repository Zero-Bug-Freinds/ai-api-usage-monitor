package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.ChangePasswordRequest;
import com.zerobugfreinds.identity_service.entity.Role;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;

	private AccountSecurityService accountSecurityService;

	@BeforeEach
	void setUp() {
		accountSecurityService = new AccountSecurityService(userRepository, passwordEncoder);
	}

	@Test
	void changePassword_updatesStoredPasswordWhenCurrentPasswordMatches() {
		User user = new User("user@example.com", "stored-hash", "tester", Role.USER);
		ReflectionTestUtils.setField(user, "id", 1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current123!@", "stored-hash")).thenReturn(true);
		when(passwordEncoder.matches("next123!@", "stored-hash")).thenReturn(false);
		when(passwordEncoder.encode("next123!@")).thenReturn("new-hash");

		accountSecurityService.changePassword(1L, new ChangePasswordRequest("current123!@", "next123!@", "next123!@"));

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getPassword()).isEqualTo("new-hash");
	}

	@Test
	void changePassword_throwsWhenCurrentPasswordIsWrong() {
		User user = new User("user@example.com", "stored-hash", "tester", Role.USER);
		ReflectionTestUtils.setField(user, "id", 1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong123!@", "stored-hash")).thenReturn(false);

		assertThatThrownBy(() -> accountSecurityService.changePassword(
				1L,
				new ChangePasswordRequest("wrong123!@", "next123!@", "next123!@")
		)).isInstanceOf(InvalidCredentialsException.class);

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void changePassword_throwsWhenNewPasswordMatchesCurrentPassword() {
		User user = new User("user@example.com", "stored-hash", "tester", Role.USER);
		ReflectionTestUtils.setField(user, "id", 1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current123!@", "stored-hash")).thenReturn(true);
		when(passwordEncoder.matches("current123!@", "stored-hash")).thenReturn(true);

		assertThatThrownBy(() -> accountSecurityService.changePassword(
				1L,
				new ChangePasswordRequest("current123!@", "current123!@", "current123!@")
		)).isInstanceOf(InvalidSignupRequestException.class);

		verify(userRepository, never()).save(any(User.class));
	}
}
