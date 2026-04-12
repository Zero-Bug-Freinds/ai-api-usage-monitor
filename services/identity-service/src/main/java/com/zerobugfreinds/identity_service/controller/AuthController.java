package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.ForgotPasswordRequest;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.LoginResponse;
import com.zerobugfreinds.identity_service.dto.ResetPasswordRequest;
import com.zerobugfreinds.identity_service.dto.SessionResponse;
import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.exception.AuthContractViolationException;
import com.zerobugfreinds.identity_service.service.PasswordResetService;
import com.zerobugfreinds.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 HTTP API.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;
	private final PasswordResetService passwordResetService;

	public AuthController(UserService userService, PasswordResetService passwordResetService) {
		this.userService = userService;
		this.passwordResetService = passwordResetService;
	}

	/**
	 * 회원가입.
	 */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		SignupResponse body = userService.signup(request);
		return ApiResponse.ok("회원가입이 완료되었습니다", body);
	}

	/**
	 * 로그인.
	 */
	/**
	 * 비밀번호 찾기: 이메일로 재설정 링크 발송(등록된 주소만 실제 발송).
	 */
	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		passwordResetService.requestForgotPassword(request);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok(PasswordResetService.FORGOT_PASSWORD_UNIFORM_MESSAGE, null));
	}

	/**
	 * 비밀번호 재설정: 메일 링크의 토큰과 새 비밀번호로 갱신.
	 */
	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("비밀번호가 변경되었습니다. 로그인해 주세요", null));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse body = userService.login(request);
		if (!"Bearer".equals(body.tokenType())) {
			throw new AuthContractViolationException("토큰 타입 계약이 올바르지 않습니다");
		}
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(ApiResponse.ok("로그인에 성공했습니다", body));
	}

	/**
	 * 세션(인증 상태) 확인.
	 */
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
				.body(ApiResponse.ok("세션이 유효합니다", body));
	}

	/**
	 * 로그아웃.
	 * Stateless 구조이므로 서버 토큰 무효화 대신, BFF가 쿠키를 삭제하도록 신호를 보낸다.
	 */
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout() {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(ApiResponse.ok("로그아웃되었습니다. BFF에서 인증 쿠키를 삭제하세요", null));
	}
}
