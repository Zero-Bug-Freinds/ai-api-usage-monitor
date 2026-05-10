package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.InternalUserPrincipalResponse;
import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.entity.Role;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.repository.RefreshTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import com.zerobugfreinds.identity_service.mq.IdentityUserSyncEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
	@Mock
	private IdentityUserSyncEventPublisher identityUserSyncEventPublisher;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(
				userRepository,
				refreshTokenRepository,
				passwordEncoder,
				jwtTokenProvider,
				teamMembershipVerificationClient,
				applicationEventPublisher,
				identityUserSyncEventPublisher
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
		User persisted = mock(User.class);
		when(persisted.getId()).thenReturn(1L);
		when(persisted.getEmail()).thenReturn("test@example.com");
		when(persisted.getName()).thenReturn("tester");
		when(persisted.getRole()).thenReturn(Role.USER);
		when(userRepository.save(any(User.class))).thenReturn(persisted);

		userService.signup(request);

		verify(userRepository).existsByEmailIgnoreCase(eq("test@example.com"));
		verify(userRepository).save(any(User.class));
		verify(identityUserSyncEventPublisher).publishAfterCommit(any());
	}

	@Test
	void resolvePrincipal_forEmail_returnsNumericUserIdAndNormalizedEmail() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(42L);
		when(user.getEmail()).thenReturn("Mix@Example.COM");
		when(userRepository.findByEmailIgnoreCase("mix@example.com")).thenReturn(Optional.of(user));

		assertThat(userService.resolvePrincipalForInternalLookup("  Mix@Example.COM  "))
				.contains(new InternalUserPrincipalResponse("42", "mix@example.com"));
	}

	@Test
	void resolvePrincipal_forNumericId_returnsSameIdAndNormalizedEmail() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(7L);
		when(user.getEmail()).thenReturn("who@Example.COM");
		when(userRepository.findById(7L)).thenReturn(Optional.of(user));

		assertThat(userService.resolvePrincipalForInternalLookup("7"))
				.contains(new InternalUserPrincipalResponse("7", "who@example.com"));
	}
}
