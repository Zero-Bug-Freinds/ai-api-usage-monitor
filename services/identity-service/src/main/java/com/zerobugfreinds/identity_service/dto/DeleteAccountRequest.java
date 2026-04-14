package com.zerobugfreinds.identity_service.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank(message = "Password is required") String password) {
}
