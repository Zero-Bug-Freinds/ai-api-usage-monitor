package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.exception.ApiKeyLimitExceededException;
import com.zerobugfreinds.identity_service.exception.AuthContractViolationException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyException;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API 공통 예외 처리 (공통 응답 형식 유지).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateEmailException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleDuplicateEmail(DuplicateEmailException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(InvalidSignupRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleInvalidSignupRequest(InvalidSignupRequestException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(AuthContractViolationException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ApiResponse<Void> handleAuthContractViolation(AuthContractViolationException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(ApiKeyLimitExceededException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleApiKeyLimitExceeded(ApiKeyLimitExceededException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(DuplicateExternalApiKeyException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleDuplicateExternalApiKey(DuplicateExternalApiKeyException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(err -> err.getDefaultMessage())
				.orElse("입력값이 올바르지 않습니다");
		return ApiResponse.fail(message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
		String detail = ex.getMostSpecificCause().getMessage();
		if (detail != null && detail.contains("ExternalApiKeyProvider")) {
			return ApiResponse.fail("provider 값이 올바르지 않습니다. 허용: GEMINI, OPENAI, ANTHROPIC");
		}
		return ApiResponse.fail("요청 본문 형식이 올바르지 않습니다");
	}
}
