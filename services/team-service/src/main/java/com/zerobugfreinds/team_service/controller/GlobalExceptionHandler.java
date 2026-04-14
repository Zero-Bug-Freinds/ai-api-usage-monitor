package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.common.ApiResponse;
import com.zerobugfreinds.team_service.exception.DuplicateTeamMemberException;
import com.zerobugfreinds.team_service.exception.DuplicateTeamInvitationException;
import com.zerobugfreinds.team_service.exception.ForbiddenTeamAccessException;
import com.zerobugfreinds.team_service.exception.InvalidTeamInvitationStateException;
import com.zerobugfreinds.team_service.exception.OwnerPermissionRequiredException;
import com.zerobugfreinds.team_service.exception.TeamDeletionBlockedException;
import com.zerobugfreinds.team_service.exception.TeamNotFoundException;
import com.zerobugfreinds.team_service.exception.TeamInvitationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
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
	public ApiResponse<Void> handleHttpMessageNotReadable() {
		return ApiResponse.fail("요청 본문 형식이 올바르지 않습니다");
	}

	@ExceptionHandler(TeamNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiResponse<Void> handleTeamNotFound(TeamNotFoundException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(ForbiddenTeamAccessException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public ApiResponse<Void> handleForbidden(ForbiddenTeamAccessException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(OwnerPermissionRequiredException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public ApiResponse<Void> handleOwnerOnly(OwnerPermissionRequiredException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(DuplicateTeamMemberException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleDuplicateTeamMember(DuplicateTeamMemberException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(DuplicateTeamInvitationException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleDuplicateTeamInvitation(DuplicateTeamInvitationException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(TeamInvitationNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiResponse<Void> handleTeamInvitationNotFound(TeamInvitationNotFoundException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(InvalidTeamInvitationStateException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleInvalidInvitationState(InvalidTeamInvitationStateException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(TeamDeletionBlockedException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiResponse<Void> handleTeamDeletionBlocked(TeamDeletionBlockedException ex) {
		return ApiResponse.fail(ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
		return ApiResponse.fail(ex.getMessage());
	}
}
