package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.InternalUserPrincipalResponse;
import com.zerobugfreinds.identity_service.dto.ProfileUpdateResponse;
import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.UpdateProfileRequest;
import com.zerobugfreinds.identity_service.entity.Role;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import com.zerobugfreinds.identity_service.repository.RefreshTokenRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import com.zerobugfreinds.identity_service.mq.IdentityUserSyncEventPublisher;
import com.zerobugfreinds.identity.events.IdentityUserSyncEventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
	@Mock
	private PlatformTransactionManager platformTransactionManager;

	private UserService userService;

	@BeforeEach
	void setUp() {
		TransactionStatus transactionStatus = mock(TransactionStatus.class);
		lenient().when(platformTransactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
		lenient().doNothing().when(platformTransactionManager).commit(any(TransactionStatus.class));
		lenient().doNothing().when(platformTransactionManager).rollback(any(TransactionStatus.class));
		when(passwordEncoder.encode(any(CharSequence.class))).thenReturn("$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

		userService = new UserService(
				userRepository,
				refreshTokenRepository,
				passwordEncoder,
				jwtTokenProvider,
				teamMembershipVerificationClient,
				applicationEventPublisher,
				identityUserSyncEventPublisher,
				platformTransactionManager
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
		when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

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
		when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
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
		when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
		when(passwordEncoder.encode("abc123!@")).thenReturn("encoded");
		User persisted = mock(User.class);
		when(persisted.getId()).thenReturn(1L);
		when(persisted.getEmail()).thenReturn("test@example.com");
		when(persisted.getName()).thenReturn("tester");
		when(persisted.getRole()).thenReturn(Role.USER);
		when(userRepository.save(any(User.class))).thenReturn(persisted);

		userService.signup(request);

		verify(userRepository).existsByEmail(eq("test@example.com"));
		verify(userRepository).save(any(User.class));
		verify(identityUserSyncEventPublisher).publishAfterCommit(any());
	}

	@Test
	void resolvePrincipal_forEmail_returnsNumericUserIdAndNormalizedEmail() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(42L);
		when(user.getEmail()).thenReturn("Mix@Example.COM");
		when(userRepository.findByEmail("mix@example.com")).thenReturn(Optional.of(user));

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

	@Test
	void updateProfile_throwsWhenBothFieldsBlank() {
		assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest("  ", "   ")))
				.isInstanceOf(InvalidSignupRequestException.class);
		assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest(null, null)))
				.isInstanceOf(InvalidSignupRequestException.class);
	}

	@Test
	void updateProfile_publishesWhenDisplayNameChanges() {
		User user = new User("a@example.com", "pw", "Old", Role.USER);
		ReflectionTestUtils.setField(user, "id", 9L);
		when(userRepository.findById(9L)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		ProfileUpdateResponse out = userService.updateProfile(9L, new UpdateProfileRequest(null, "New"));

		assertThat(out.name()).isEqualTo("New");
		assertThat(out.email()).isEqualTo("a@example.com");
		verify(identityUserSyncEventPublisher).publishAfterCommit(
				argThat(
						ev -> IdentityUserSyncEventTypes.USER_PROFILE_UPDATED.equals(ev.eventType())
								&& "a@example.com".equals(ev.userId())
				)
		);
	}

	@Test
	void updateProfile_skipsPublishWhenNoEffectiveChange() {
		User user = new User("a@example.com", "pw", "Same", Role.USER);
		ReflectionTestUtils.setField(user, "id", 3L);
		when(userRepository.findById(3L)).thenReturn(Optional.of(user));

		userService.updateProfile(3L, new UpdateProfileRequest(null, "Same"));

		verify(userRepository, never()).save(any());
		verify(identityUserSyncEventPublisher, never()).publishAfterCommit(any());
	}

	@Test
	void updateProfile_throwsDuplicateWhenEmailTakenByOther() {
		User self = new User("self@example.com", "pw", "Me", Role.USER);
		ReflectionTestUtils.setField(self, "id", 1L);
		User other = new User("taken@example.com", "pw2", "Other", Role.USER);
		ReflectionTestUtils.setField(other, "id", 2L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(self));
		when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest("taken@example.com", null)))
				.isInstanceOf(DuplicateEmailException.class);
	}
}
