package com.zerobugfreinds.team_service.common;

public record ApiResponse<T>(boolean success, String message, T data) {
	public static <T> ApiResponse<T> ok(String message, T data) {
		return new ApiResponse<>(true, message, data);
	}

	public static ApiResponse<Void> fail(String message) {
		return new ApiResponse<>(false, message, null);
	}
}
