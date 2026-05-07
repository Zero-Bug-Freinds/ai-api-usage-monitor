package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class AgentApiExceptionHandler {

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
		if ("AI_INFERENCE_FAILED".equals(ex.getMessage())) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new ApiErrorResponse(
							"AI_INFERENCE_FAILED",
							"AI 예측에 실패했습니다. Gemini API 키/모델 설정과 네트워크 상태를 확인한 뒤 다시 시도하세요.",
							Instant.now()
					));
		}
		throw ex;
	}
}
