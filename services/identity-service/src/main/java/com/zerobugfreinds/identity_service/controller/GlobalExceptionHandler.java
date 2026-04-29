package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.exception.ApiKeyLimitExceededException;
import com.zerobugfreinds.identity_service.exception.AuthContractViolationException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyAliasException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyException;
import com.zerobugfreinds.identity_service.exception.DuplicateEmailException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyAlreadyPendingDeletionException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotFoundException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotPendingDeletionException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyPendingDeletionException;
import com.zerobugfreinds.identity_service.exception.InvalidCredentialsException;
import com.zerobugfreinds.identity_service.exception.InvalidPasswordResetTokenException;
import com.zerobugfreinds.identity_service.exception.InvalidSignupRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static ApiResponse<Void> failWithFallback(String message, String fallback) {
		if (message == null || message.isBlank()) {
			return ApiResponse.fail(fallback);
		}
		return ApiResponse.fail(message);
	}

	@ExceptionHandler(DuplicateEmailException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleDuplicateEmail(DuplicateEmailException ex) {
		return failWithFallback(ex.getMessage(), "이메일이 이미 존재합니다");
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException ex) {
		return failWithFallback(ex.getMessage(), "인증이 필요합니다");
	}

	@ExceptionHandler(InvalidSignupRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleInvalidSignupRequest(InvalidSignupRequestException ex) {
		return failWithFallback(ex.getMessage(), "회원가입 요청이 올바르지 않습니다");
	}

	@ExceptionHandler(InvalidPasswordResetTokenException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
		return failWithFallback(ex.getMessage(), "비밀번호 재설정 토큰이 유효하지 않습니다");
	}

	@ExceptionHandler(AuthContractViolationException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ApiResponse<Void> handleAuthContractViolation(AuthContractViolationException ex) {
		return failWithFallback(ex.getMessage(), "인증 계약 위반이 발생했습니다");
	}

	@ExceptionHandler(ApiKeyLimitExceededException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleApiKeyLimitExceeded(ApiKeyLimitExceededException ex) {
		return failWithFallback(ex.getMessage(), "API 키 개수 제한을 초과했습니다");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("identity bad request IllegalArgumentException message={}", ex.getMessage(), ex);
		return failWithFallback(ex.getMessage(), "요청 값이 올바르지 않습니다");
	}

	@ExceptionHandler(DuplicateExternalApiKeyException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleDuplicateExternalApiKey(DuplicateExternalApiKeyException ex) {
		return failWithFallback(ex.getMessage(), "이미 등록된 외부 API 키입니다");
	}

	@ExceptionHandler(DuplicateExternalApiKeyAliasException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleDuplicateExternalApiKeyAlias(DuplicateExternalApiKeyAliasException ex) {
		return failWithFallback(ex.getMessage(), "이미 사용 중인 API 키 별칭입니다");
	}

	@ExceptionHandler(ExternalApiKeyNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiResponse<Void> handleExternalApiKeyNotFound(ExternalApiKeyNotFoundException ex) {
		return failWithFallback(ex.getMessage(), "외부 API 키를 찾을 수 없습니다");
	}

	@ExceptionHandler(ExternalApiKeyAlreadyPendingDeletionException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleExternalApiKeyAlreadyPendingDeletion(ExternalApiKeyAlreadyPendingDeletionException ex) {
		return failWithFallback(ex.getMessage(), "이미 삭제 예정 상태입니다");
	}

	@ExceptionHandler(ExternalApiKeyNotPendingDeletionException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleExternalApiKeyNotPendingDeletion(ExternalApiKeyNotPendingDeletionException ex) {
		return failWithFallback(ex.getMessage(), "삭제 예정 상태가 아닙니다");
	}

	@ExceptionHandler(ExternalApiKeyPendingDeletionException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleExternalApiKeyPendingDeletion(ExternalApiKeyPendingDeletionException ex) {
		return failWithFallback(ex.getMessage(), "삭제 예정 API 키는 수정할 수 없습니다");
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
