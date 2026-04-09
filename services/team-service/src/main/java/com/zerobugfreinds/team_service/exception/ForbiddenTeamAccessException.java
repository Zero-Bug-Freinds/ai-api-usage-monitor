package com.zerobugfreinds.team_service.exception;

public class ForbiddenTeamAccessException extends RuntimeException {
	public ForbiddenTeamAccessException(String message) {
		super(message);
	}
}
