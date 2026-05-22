package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyResponse;
import com.zerobugfreinds.team_service.service.TeamTrustedApiKeyCredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/team-api-keys")
public class InternalTeamApiKeyCredentialController {

    private final TeamTrustedApiKeyCredentialService teamTrustedApiKeyCredentialService;

    public InternalTeamApiKeyCredentialController(
            TeamTrustedApiKeyCredentialService teamTrustedApiKeyCredentialService
    ) {
        this.teamTrustedApiKeyCredentialService = teamTrustedApiKeyCredentialService;
    }

    @GetMapping("/{keyId}/credential")
    public ResponseEntity<InternalTeamApiKeyResponse> resolveCredential(
            @PathVariable("keyId") String keyId,
            @RequestParam("teamId") Long teamId,
            @RequestParam("provider") String provider,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        InternalTeamApiKeyResponse response = teamTrustedApiKeyCredentialService.resolveCredential(
                keyId,
                teamId,
                provider,
                authorizationHeader
        );
        return ResponseEntity.ok(response);
    }
}
