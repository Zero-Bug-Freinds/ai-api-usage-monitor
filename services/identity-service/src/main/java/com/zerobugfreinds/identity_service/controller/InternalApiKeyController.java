package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyResponse;
import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Proxy 내부 호출용 API 키 조회 API.
 */
@RestController
@RequestMapping("/internal/api-keys")
public class InternalApiKeyController {

	private final ExternalApiKeyService externalApiKeyService;

	public InternalApiKeyController(ExternalApiKeyService externalApiKeyService) {
		this.externalApiKeyService = externalApiKeyService;
	}

	@GetMapping("/{provider}")
	public ResponseEntity<InternalApiKeyResponse> getByProvider(
			@PathVariable String provider,
			@RequestParam Long userId
	) {
		try {
			ExternalApiKeyProvider externalApiKeyProvider = ExternalApiKeyProvider.fromInternalPathSegment(provider);
			return ResponseEntity.ok(externalApiKeyService.resolveInternalKey(userId, externalApiKeyProvider));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
		}
	}
}
