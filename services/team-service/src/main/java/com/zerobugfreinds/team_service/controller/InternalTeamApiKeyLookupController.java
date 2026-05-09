package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyLookupResponse;
import com.zerobugfreinds.team_service.service.TeamApiKeyLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀 API 키 역조회(Reverse Lookup) 내부 API.
 *
 * <p>Proxy 등 내부 호출자가 클라이언트에게 받은 외부 API 키의 해시값으로
 * 어느 팀/등록 멤버/키에 매핑되는지 식별할 때 사용한다. 평문 키는 응답에 포함하지 않는다.</p>
 *
 * <p>경로를 {@code /internal/v1/team-api-keys} 로 분리해 기존 팀별 단순 조회 API
 * ({@link InternalTeamApiKeyController}) 와 충돌하지 않는다.</p>
 */
@RestController
@RequestMapping("/internal/v1/team-api-keys")
public class InternalTeamApiKeyLookupController {

    private final TeamApiKeyLookupService teamApiKeyLookupService;

    public InternalTeamApiKeyLookupController(TeamApiKeyLookupService teamApiKeyLookupService) {
        this.teamApiKeyLookupService = teamApiKeyLookupService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<InternalTeamApiKeyLookupResponse> lookupByHashedKey(
            @RequestParam("hashedKey") String hashedKey,
            @RequestParam("provider") String provider,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        InternalTeamApiKeyLookupResponse response =
                teamApiKeyLookupService.lookupByHashedKey(provider, hashedKey, authorizationHeader);
        return ResponseEntity.ok(response);
    }
}
