package com.zerobugfreinds.team_service.exception;

public class ApiKeyRegistrationLockBusyException extends RuntimeException {

    public ApiKeyRegistrationLockBusyException(String message) {
        super(message);
    }
}
