package com.zerobugfreinds.team_service.exception;

public class InternalRequestUnauthorizedException extends RuntimeException {
    public InternalRequestUnauthorizedException(String message) {
        super(message);
    }
}
