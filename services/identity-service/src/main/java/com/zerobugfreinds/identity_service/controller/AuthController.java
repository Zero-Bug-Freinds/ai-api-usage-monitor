package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.ChangePasswordRequest;
import com.zerobugfreinds.identity_service.dto.DeleteAccountRequest;
import com.zerobugfreinds.identity_service.dto.ForgotPasswordRequest;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.ResetPasswordRequest;
import com.zerobugfreinds.identity_service.dto.ProfileUpdateResponse;
import com.zerobugfreinds.identity_service.dto.SessionResponse;
import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.dto.SwitchTeamRequest;
import com.zerobugfreinds.identity_service.dto.TokenResponse;
import com.zerobugfreinds.identity_service.dto.UpdateProfileRequest;
import com.zerobugfreinds.identity_service.exception.AuthContractViolationException;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.service.AccountDeletionService;
import com.zerobugfreinds.identity_service.service.AccountSecurityService;
import com.zerobugfreinds.identity_service.service.PasswordResetService;
import com.zerobugfreinds.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication HTTP API.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;
	private final PasswordResetService passwordResetService;
	private final AccountSecurityService accountSecurityService;
	private final AccountDeletionService accountDeletionService;

	public AuthController(
			UserService userService,
			PasswordResetService passwordResetService,
			AccountSecurityService accountSecurityService,
			AccountDeletionService accountDeletionService
	) {
		this.userService = userService;
		this.passwordResetService = passwordResetService;
		this.accountSecurityService = accountSecurityService;
		this.accountDeletionService = accountDeletionService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		SignupResponse body = userService.signup(request);
		return ApiResponse.ok("Signup completed", body);
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		passwordResetService.requestForgotPassword(request);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok(PasswordResetService.FORGOT_PASSWORD_UNIFORM_MESSAGE, null));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("Password updated. Please sign in.", null));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
		TokenResponse body = userService.login(request);
		if (!"Bearer".equals(body.tokenType())) {
			throw new AuthContractViolationException("Token type contract violation");
		}
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("Login successful", body));
	}

	@PostMapping("/change-password")
	public ResponseEntity<ApiResponse<Void>> changePassword(
			Authentication authentication,
			@Valid @RequestBody ChangePasswordRequest request
	) {
		User user = userService.findByAuthenticatedPrincipal(authentication.getName());
		accountSecurityService.changePassword(user.getId(), request);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("비밀번호가 변경되었습니다", null));
	}

	@PutMapping("/profile")
	public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
			Authentication authentication,
			@Valid @RequestBody UpdateProfileRequest request
	) {
		User user = userService.findByAuthenticatedPrincipal(authentication.getName());
		ProfileUpdateResponse body = userService.updateProfile(user.getId(), request);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("Profile updated", body));
	}

	@GetMapping("/session")
	public ResponseEntity<ApiResponse<SessionResponse>> session(Authentication authentication) {
		User user = userService.findByAuthenticatedPrincipal(authentication.getName());
		String authority = authentication.getAuthorities().stream()
				.findFirst()
				.map(granted -> granted.getAuthority())
				.orElse("ROLE_USER");
		String role = authority.startsWith("ROLE_") ? authority.substring(5) : authority;

		SessionResponse body = new SessionResponse(user.getEmail(), role, true);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("Session valid", body));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout() {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(ApiResponse.ok("Signed out. Clear auth cookie on BFF.", null));
	}

	@PostMapping({"/switch-team", "/token/switch-team"})
	public ResponseEntity<ApiResponse<TokenResponse>> switchTeam(
			Authentication authentication,
			@Valid @RequestBody SwitchTeamRequest request
	) {
		User user = userService.findByAuthenticatedPrincipal(authentication.getName());
		TokenResponse body = userService.switchTeam(user.getId(), request.targetTeamId());
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("Team context switched", body));
	}

	/**
	 * 비밀번호 확인 후 identity 사용자 행을 즉시 삭제하고 연동 서비스 정리 이벤트를 발행한다.
	 * {@code account_deletion_pending} 는 billing·usage·team ACK 수집 후에만 제거된다.
	 */
	@PostMapping("/delete-account")
	public ResponseEntity<ApiResponse<Void>> deleteAccount(
			Authentication authentication,
			@Valid @RequestBody DeleteAccountRequest request
	) {
		User user = userService.findByAuthenticatedPrincipal(authentication.getName());
		accountDeletionService.deleteAuthenticatedAccount(user.getId(), request.password());
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok(
						"회원 탈퇴가 완료되었습니다. 계정에 다시 로그인할 수 없습니다.",
						null
				));
	}
}
