package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.ExternalApiKeyRegisterRequest;
import com.zerobugfreinds.identity_service.dto.ExternalApiKeyRegisterResponse;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.security.IdentityUserPrincipal;
import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
				request.externalKey()
		);
		ExternalApiKeyRegisterResponse data = new ExternalApiKeyRegisterResponse(
				saved.getId(),
				saved.getProvider().name(),
				saved.getKeyAlias(),
				saved.getCreatedAt()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("외부 API 키가 등록되었습니다", data));
	}

	@GetMapping("/external-keys")
	public ResponseEntity<ApiResponse<List<ExternalApiKeyRegisterResponse>>> getMyKeys(
			@AuthenticationPrincipal IdentityUserPrincipal principal
	) {
		List<ExternalApiKeyRegisterResponse> data = externalApiKeyService.getMyKeys(principal.userId()).stream()
				.map(key -> new ExternalApiKeyRegisterResponse(
						key.getId(),
						key.getProvider().name(),
						key.getKeyAlias(),
						key.getCreatedAt()
				))
				.toList();
		return ResponseEntity.ok(ApiResponse.ok("외부 API 키 목록 조회에 성공했습니다", data));
	}
}
