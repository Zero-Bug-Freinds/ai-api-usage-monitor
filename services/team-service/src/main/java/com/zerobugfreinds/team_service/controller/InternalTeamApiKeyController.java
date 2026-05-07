package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyResponse;
import com.zerobugfreinds.team_service.service.TeamInternalApiKeyResolveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api-keys")
public class InternalTeamApiKeyController {
    private final TeamInternalApiKeyResolveService teamInternalApiKeyResolveService;

    public InternalTeamApiKeyController(TeamInternalApiKeyResolveService teamInternalApiKeyResolveService) {
        this.teamInternalApiKeyResolveService = teamInternalApiKeyResolveService;
    }

    @GetMapping("/{provider}")
    public ResponseEntity<InternalTeamApiKeyResponse> resolveTeamApiKey(
            @PathVariable("provider") String provider,
            @RequestParam("teamId") Long teamId,
            @RequestParam("userId") String userId,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        InternalTeamApiKeyResponse response =
                teamInternalApiKeyResolveService.resolve(provider, teamId, userId, authorizationHeader);
        return ResponseEntity.ok(response);
    }
}
