package com.zerobugfreinds.team_service.exception;

public class DuplicateTeamInvitationException extends RuntimeException {
	public DuplicateTeamInvitationException(String message) {
		super(message);
	}
}
