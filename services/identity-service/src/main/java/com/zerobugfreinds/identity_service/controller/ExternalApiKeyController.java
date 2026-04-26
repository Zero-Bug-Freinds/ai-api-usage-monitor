package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.ExternalApiKeyRegisterRequest;
import com.zerobugfreinds.identity_service.dto.ExternalApiKeyRegisterResponse;
import com.zerobugfreinds.identity_service.dto.ExternalApiKeyUpdateRequest;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.security.IdentityUserPrincipal;
import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 외부 AI API 키 등록 HTTP API.
 */
@RestController
@RequestMapping("/api/auth")
public class ExternalApiKeyController {

	private final ExternalApiKeyService externalApiKeyService;

	public ExternalApiKeyController(ExternalApiKeyService externalApiKeyService) {
		this.externalApiKeyService = externalApiKeyService;
	}

	@PostMapping("/external-keys")
	public ResponseEntity<ApiResponse<ExternalApiKeyRegisterResponse>> register(
			@AuthenticationPrincipal IdentityUserPrincipal principal,
			@Valid @RequestBody ExternalApiKeyRegisterRequest request
	) {
		ExternalApiKeyEntity saved = externalApiKeyService.register(
				principal.userId(),
				request.provider(),
				request.alias(),
				request.externalKey(),
				request.monthlyBudgetUsd()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("외부 API 키가 등록되었습니다", toResponse(saved)));
	}

	@GetMapping("/external-keys")
	public ResponseEntity<ApiResponse<List<ExternalApiKeyRegisterResponse>>> getMyKeys(
			@AuthenticationPrincipal IdentityUserPrincipal principal
	) {
		List<ExternalApiKeyRegisterResponse> data = externalApiKeyService.getMyKeys(principal.userId()).stream()
				.map(ExternalApiKeyController::toResponse)
				.toList();
		return ResponseEntity.ok(ApiResponse.ok("외부 API 키 목록 조회에 성공했습니다", data));
	}

	@PutMapping("/external-keys/{id}")
	public ResponseEntity<ApiResponse<ExternalApiKeyRegisterResponse>> update(
			@AuthenticationPrincipal IdentityUserPrincipal principal,
			@PathVariable("id") Long id,
			@Valid @RequestBody ExternalApiKeyUpdateRequest request
	) {
		ExternalApiKeyEntity saved = externalApiKeyService.update(
				principal.userId(),
				id,
				request.provider(),
				request.alias(),
				request.externalKey(),
				request.monthlyBudgetUsd()
		);
		return ResponseEntity.ok(ApiResponse.ok("외부 API 키가 수정되었습니다", toResponse(saved)));
	}

	/**
	 * 삭제 요청: {@code gracePeriodDays=0}이면 즉시 물리 삭제. 그 외에는 유예(기본 7일) 후 스케줄러가 행을 제거하며 유예 중 취소 가능.
	 * {@code retainLogs=false}이면 키가 최종적으로 삭제되는 시점(즉시/유예 후)에 usage 쪽 사용 로그·메타데이터 정리를 요청한다.
	 */
	@DeleteMapping("/external-keys/{id}")
	public ResponseEntity<ApiResponse<ExternalApiKeyRegisterResponse>> requestDeletion(
			@AuthenticationPrincipal IdentityUserPrincipal principal,
			@PathVariable("id") Long id,
			@RequestParam(name = "gracePeriodDays", required = false) Integer gracePeriodDays,
			@RequestParam(name = "retainLogs", defaultValue = "true") boolean retainLogs
	) {
		ExternalApiKeyEntity saved = externalApiKeyService.requestDeletion(principal.userId(), id, gracePeriodDays, retainLogs);
		boolean immediate = gracePeriodDays != null && gracePeriodDays == 0;
		String message = immediate
				? "API 키가 즉시 영구 삭제되었습니다."
				: "삭제가 예약되었습니다. 유예 기간 안에 취소할 수 있으며, 종료 후에는 키가 영구 삭제됩니다.";
		return ResponseEntity.ok(ApiResponse.ok(message, toResponse(saved)));
	}

	@PostMapping("/external-keys/{id}/deletion-cancel")
	public ResponseEntity<ApiResponse<ExternalApiKeyRegisterResponse>> cancelDeletion(
			@AuthenticationPrincipal IdentityUserPrincipal principal,
			@PathVariable("id") Long id
	) {
		ExternalApiKeyEntity saved = externalApiKeyService.cancelDeletion(principal.userId(), id);
		return ResponseEntity.ok(ApiResponse.ok("삭제 예약이 취소되었습니다", toResponse(saved)));
	}

	private static ExternalApiKeyRegisterResponse toResponse(ExternalApiKeyEntity key) {
		return new ExternalApiKeyRegisterResponse(
				key.getId(),
				key.getProvider().name(),
				key.getKeyAlias(),
				key.getCreatedAt(),
				key.getMonthlyBudgetUsd(),
				key.getDeletionRequestedAt(),
				key.getPermanentDeletionAt(),
				key.getDeletionGraceDays()
		);
	}
}
