package com.zerobugfreinds.team_service.exception;

public class TeamApiKeyNotFoundException extends RuntimeException {
    public TeamApiKeyNotFoundException(String message) {
        super(message);
    }
}
