package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.common.ApiResponse;
import com.zerobugfreinds.team_service.dto.InternalTeamDetailResponse;
import com.zerobugfreinds.team_service.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 서비스 간 호출용 팀 조회 API.
 */
@RestController
@RequestMapping("/internal")
public class InternalTeamController {

	private final TeamService teamService;

	public InternalTeamController(TeamService teamService) {
		this.teamService = teamService;
	}

	@GetMapping("/teams/{id}")
	public ResponseEntity<ApiResponse<InternalTeamDetailResponse>> getTeamDetail(
			@PathVariable("id") Long teamId
	) {
		InternalTeamDetailResponse detail = teamService.getTeamDetailInternal(teamId);
		return ResponseEntity.ok(ApiResponse.ok("팀 상세 조회에 성공했습니다", detail));
	}
}
