package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.common.ApiResponse;
import com.zerobugfreinds.team_service.dto.TeamIdentityConsistencyResponse;
import com.zerobugfreinds.team_service.service.TeamIdentityConsistencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin")
public class InternalTeamAdminController {
	private final TeamIdentityConsistencyService teamIdentityConsistencyService;

	public InternalTeamAdminController(TeamIdentityConsistencyService teamIdentityConsistencyService) {
		this.teamIdentityConsistencyService = teamIdentityConsistencyService;
	}

	@GetMapping("/consistency/identity-users")
	public ResponseEntity<ApiResponse<TeamIdentityConsistencyResponse>> getIdentityConsistency() {
		TeamIdentityConsistencyResponse response = teamIdentityConsistencyService.findZombieUsers();
		return ResponseEntity.ok(ApiResponse.ok("Identity-Team 데이터 정합성 점검이 완료되었습니다", response));
	}
}
