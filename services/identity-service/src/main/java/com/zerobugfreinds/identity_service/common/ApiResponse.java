package com.zerobugfreinds.identity_service.common;

/**
 * 모든 API 응답을 일관되게 감싸기 위한 공통 응답 객체.
 *
 * @param <T> 응답 데이터 타입
 */
public record ApiResponse<T>(boolean success, String message, T data) {

	/**
	 * 성공 응답 생성.
	 */
	public static <T> ApiResponse<T> ok(String message, T data) {
		return new ApiResponse<>(true, message, data);
	}

	/**
	 * 실패 응답 생성 (데이터 없음).
	 */
	public static ApiResponse<Void> fail(String message) {
		return new ApiResponse<>(false, message, null);
	}
}
