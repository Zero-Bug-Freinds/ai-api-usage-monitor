package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.common.ApiResponse;
import com.zerobugfreinds.team_service.dto.CreateTeamRequest;
import com.zerobugfreinds.team_service.dto.InviteTeamMemberRequest;
import com.zerobugfreinds.team_service.dto.TeamSummaryResponse;
import com.zerobugfreinds.team_service.security.TeamUserPrincipal;
import com.zerobugfreinds.team_service.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TeamController {
	private final TeamService teamService;

	public TeamController(TeamService teamService) {
		this.teamService = teamService;
	}

	@PostMapping("/teams")
	public ResponseEntity<ApiResponse<TeamSummaryResponse>> createTeam(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@Valid @RequestBody CreateTeamRequest request
	) {
		TeamSummaryResponse created = teamService.createTeam(principal.userId(), request.name());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("팀이 생성되었습니다", created));
	}

	@GetMapping("/me/teams")
	public ResponseEntity<ApiResponse<List<TeamSummaryResponse>>> getMyTeams(
			@AuthenticationPrincipal TeamUserPrincipal principal
	) {
		List<TeamSummaryResponse> teams = teamService.getMyTeams(principal.userId());
		return ResponseEntity.ok(ApiResponse.ok("팀 목록 조회에 성공했습니다", teams));
	}

	@PostMapping("/teams/{id}/members")
	public ResponseEntity<ApiResponse<TeamSummaryResponse>> inviteMember(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("id") Long teamId,
			@Valid @RequestBody InviteTeamMemberRequest request
	) {
		TeamSummaryResponse team = teamService.inviteMember(principal.userId(), teamId, request.userId().trim());
		return ResponseEntity.ok(ApiResponse.ok("팀 초대가 완료되었습니다", team));
	}

	@GetMapping("/teams/{id}/members")
	public ResponseEntity<ApiResponse<List<String>>> getTeamMembers(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("id") Long teamId
	) {
		List<String> members = teamService.getTeamMemberUserIds(principal.userId(), teamId);
		return ResponseEntity.ok(ApiResponse.ok("팀 멤버 조회에 성공했습니다", members));
	}
}
