package com.zerobugfreinds.team_service.exception;

public class DuplicateTeamMemberException extends RuntimeException {
	public DuplicateTeamMemberException(String message) {
		super(message);
	}
}
