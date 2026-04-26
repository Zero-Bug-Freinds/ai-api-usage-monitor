package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.DeleteAccountRequest;
import com.zerobugfreinds.identity_service.dto.ForgotPasswordRequest;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.ResetPasswordRequest;
import com.zerobugfreinds.identity_service.dto.SessionResponse;
import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.dto.SwitchTeamRequest;
import com.zerobugfreinds.identity_service.dto.TokenResponse;
import com.zerobugfreinds.identity_service.exception.AuthContractViolationException;
import com.zerobugfreinds.identity_service.security.IdentityUserPrincipal;
import com.zerobugfreinds.identity_service.service.AccountDeletionService;
import com.zerobugfreinds.identity_service.service.PasswordResetService;
import com.zerobugfreinds.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Authentication HTTP API.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;
	private final PasswordResetService passwordResetService;
	private final AccountDeletionService accountDeletionService;

	public AuthController(
			UserService userService,
			PasswordResetService passwordResetService,
			AccountDeletionService accountDeletionService
	) {
		this.userService = userService;
		this.passwordResetService = passwordResetService;
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

	@GetMapping("/session")
	public ResponseEntity<ApiResponse<SessionResponse>> session(Authentication authentication) {
		String authority = authentication.getAuthorities().stream()
				.findFirst()
				.map(granted -> granted.getAuthority())
				.orElse("ROLE_USER");
		String role = authority.startsWith("ROLE_") ? authority.substring(5) : authority;

		SessionResponse body = new SessionResponse(authentication.getName(), role, true);
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
			@AuthenticationPrincipal IdentityUserPrincipal principal,
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
			@Valid @RequestBody SwitchTeamRequest request
	) {
		TokenResponse body = userService.switchTeam(principal.userId(), request.targetTeamId(), authorization);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("Team context switched", body));
	}

	/**
	 * 삭제 요청 이벤트 발행까지 완료하면 응답한다. identity 사용자 행 제거는 연동 서비스 ACK 후 비동기로 진행된다.
	 */
	@PostMapping("/delete-account")
	public ResponseEntity<ApiResponse<Void>> deleteAccount(
			@AuthenticationPrincipal IdentityUserPrincipal principal,
			@Valid @RequestBody DeleteAccountRequest request
	) {
		accountDeletionService.deleteAuthenticatedAccount(principal, request.password());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok(
						"\uC68D\uC6D0 \uC0CC\uD1F4 \uC694\uCCAD\uC774 \uC811\uC218\uB418\uC5C8\uC2B5\uB2C8\uB2E4. "
								+ "\uC5F0\uB3D9 \uC11C\uBE44\uC2A4 \uC0AD\uC81C \uD655\uC778 \uD6C4 \uACC4\uC815\uC774 \uC81C\uAC70\uB429\uB2C8\uB2E4.",
						null
				));
	}
}
