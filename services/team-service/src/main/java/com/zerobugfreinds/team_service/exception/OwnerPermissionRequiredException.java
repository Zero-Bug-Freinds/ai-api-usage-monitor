package com.zerobugfreinds.team_service.exception;

public class OwnerPermissionRequiredException extends RuntimeException {
	public OwnerPermissionRequiredException(String message) {
		super(message);
	}
}
