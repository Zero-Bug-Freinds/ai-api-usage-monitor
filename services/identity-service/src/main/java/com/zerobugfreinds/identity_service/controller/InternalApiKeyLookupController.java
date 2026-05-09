package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyLookupResponse;
import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 외부 API 키 역조회(Reverse Lookup) 내부 API.
 *
 * <p>Proxy 등 내부 호출자가 클라이언트에게 받은 외부 API 키의 해시값으로
 * 어느 사용자/키에 매핑되는지 식별할 때 사용한다. 평문 키는 응답에 포함하지 않는다.</p>
 *
 * <p>경로를 {@code /internal/v1/api-keys} 로 분리해 기존 사용자별 단순 조회 API
 * ({@link InternalApiKeyController}) 와 충돌하지 않는다.</p>
 */
@RestController
@RequestMapping("/internal/v1/api-keys")
public class InternalApiKeyLookupController {

	private final ExternalApiKeyService externalApiKeyService;

	public InternalApiKeyLookupController(ExternalApiKeyService externalApiKeyService) {
		this.externalApiKeyService = externalApiKeyService;
	}

	@GetMapping("/lookup")
	public ResponseEntity<InternalApiKeyLookupResponse> lookupByHashedKey(
			@RequestParam("hashedKey") String hashedKey,
			@RequestParam("provider") String provider
	) {
		ExternalApiKeyProvider parsedProvider = parseProvider(provider);
		InternalApiKeyLookupResponse response =
				externalApiKeyService.lookupByHashedKey(parsedProvider, hashedKey);
		return ResponseEntity.ok(response);
	}

	private static ExternalApiKeyProvider parseProvider(String provider) {
		if (provider == null || provider.isBlank()) {
			throw new ResponseStatusException(BAD_REQUEST, "provider는 필수입니다");
		}
		String normalized = provider.trim();
		if (!normalized.equals(normalized.toUpperCase(Locale.ROOT))) {
			throw new ResponseStatusException(BAD_REQUEST, "provider는 대문자 enum 이름만 허용합니다");
		}
		try {
			return ExternalApiKeyProvider.valueOf(normalized);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(BAD_REQUEST, "지원하지 않는 provider입니다: " + provider, ex);
		}
	}
}
