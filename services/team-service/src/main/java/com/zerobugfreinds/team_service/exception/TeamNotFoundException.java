package com.zerobugfreinds.team_service.exception;

public class TeamNotFoundException extends RuntimeException {
	public TeamNotFoundException(String message) {
		super(message);
	}
}
