package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.entity.Role;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.repository.RefreshTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtTokenProvider jwtTokenProvider;
	@Mock
	private TeamMembershipVerificationClient teamMembershipVerificationClient;
	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(
				userRepository,
				refreshTokenRepository,
				passwordEncoder,
				jwtTokenProvider,
				teamMembershipVerificationClient,
				applicationEventPublisher
		);
	}

	@Test
	void signup_throwsWhenDuplicateEmailIgnoringCase() {
		SignupRequest request = new SignupRequest(
				"  Test@Example.com  ",
				"abc123!@",
				"abc123!@",
				"tester",
				Role.USER
		);
		when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.signup(request))
				.isInstanceOf(DuplicateEmailException.class);
	}

	@Test
	void signup_throwsDuplicateEmailWhenSaveHitsUniqueConstraint() {
		SignupRequest request = new SignupRequest(
				"test@example.com",
				"abc123!@",
				"abc123!@",
				"tester",
				Role.USER
		);
		when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
		when(passwordEncoder.encode("abc123!@")).thenReturn("encoded");
		when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> userService.signup(request))
				.isInstanceOf(DuplicateEmailException.class);
	}

	@Test
	void signup_savesNormalizedLowercaseEmail() {
		SignupRequest request = new SignupRequest(
				"  Test@Example.com  ",
				"abc123!@",
				"abc123!@",
				"tester",
				Role.USER
		);
		when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
		when(passwordEncoder.encode("abc123!@")).thenReturn("encoded");
		when(userRepository.save(any(User.class)))
				.thenReturn(new User("test@example.com", "encoded", "tester", Role.USER));

		userService.signup(request);

		verify(userRepository).existsByEmailIgnoreCase(eq("test@example.com"));
		verify(userRepository).save(any(User.class));
	}
}
