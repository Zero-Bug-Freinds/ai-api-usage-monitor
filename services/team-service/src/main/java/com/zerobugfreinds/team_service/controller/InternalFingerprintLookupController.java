package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.dto.InternalFingerprintLookupRequest;
import com.zerobugfreinds.team_service.dto.InternalFingerprintLookupResponse;
import com.zerobugfreinds.team_service.security.ApiInternalLookupAuthValidator;
import com.zerobugfreinds.team_service.service.TeamApiKeyFingerprintLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 원시 API 키 fingerprint 로 팀 소유 키를 식별하는 내부 POST API (identity-service 와 동일 경로).
 */
@RestController
@RequestMapping("/internal/v1/api-keys")
public class InternalFingerprintLookupController {

    private final TeamApiKeyFingerprintLookupService teamApiKeyFingerprintLookupService;
    private final ApiInternalLookupAuthValidator apiInternalLookupAuthValidator;

    public InternalFingerprintLookupController(
            TeamApiKeyFingerprintLookupService teamApiKeyFingerprintLookupService,
            ApiInternalLookupAuthValidator apiInternalLookupAuthValidator
    ) {
        this.teamApiKeyFingerprintLookupService = teamApiKeyFingerprintLookupService;
        this.apiInternalLookupAuthValidator = apiInternalLookupAuthValidator;
    }

    @PostMapping("/lookup")
    public ResponseEntity<InternalFingerprintLookupResponse> lookup(
            @RequestBody InternalFingerprintLookupRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        apiInternalLookupAuthValidator.validateBearer(authorizationHeader);
        InternalFingerprintLookupResponse body = teamApiKeyFingerprintLookupService.lookup(
                request.provider(),
                request.fingerprint()
        );
        return ResponseEntity.ok(body);
    }
}
