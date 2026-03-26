package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.LoginRequest;
import com.zerobugfreinds.identity_service.dto.LoginResponse;
import com.zerobugfreinds.identity_service.dto.SignupRequest;
import com.zerobugfreinds.identity_service.dto.SignupResponse;
import com.zerobugfreinds.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
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

	public AuthController(UserService userService) {
		this.userService = userService;
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
	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse body = userService.login(request);
		return ApiResponse.ok("로그인에 성공했습니다", body);
	}
}
