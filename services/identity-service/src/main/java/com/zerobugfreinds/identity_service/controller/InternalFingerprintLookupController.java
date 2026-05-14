package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.dto.InternalFingerprintLookupRequest;
import com.zerobugfreinds.identity_service.dto.InternalFingerprintLookupResponse;
import com.zerobugfreinds.identity_service.security.ApiInternalLookupAuthValidator;
import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 원시 API 키의 SHA-256 fingerprint 로 소유자(개인)를 식별하는 내부 POST API.
 */
@RestController
@RequestMapping("/internal/v1/api-keys")
public class InternalFingerprintLookupController {

	private final ExternalApiKeyService externalApiKeyService;
	private final ApiInternalLookupAuthValidator apiInternalLookupAuthValidator;

	public InternalFingerprintLookupController(
			ExternalApiKeyService externalApiKeyService,
			ApiInternalLookupAuthValidator apiInternalLookupAuthValidator
	) {
		this.externalApiKeyService = externalApiKeyService;
		this.apiInternalLookupAuthValidator = apiInternalLookupAuthValidator;
	}

	@PostMapping("/lookup")
	public ResponseEntity<InternalFingerprintLookupResponse> lookup(
			@RequestBody InternalFingerprintLookupRequest request,
			@RequestHeader(name = "Authorization", required = false) String authorizationHeader
	) {
		apiInternalLookupAuthValidator.validateBearer(authorizationHeader);
		ExternalApiKeyProvider provider = parseProvider(request.provider());
		InternalFingerprintLookupResponse body =
				externalApiKeyService.lookupByApiKeyFingerprint(provider, request.fingerprint());
		return ResponseEntity.ok(body);
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
