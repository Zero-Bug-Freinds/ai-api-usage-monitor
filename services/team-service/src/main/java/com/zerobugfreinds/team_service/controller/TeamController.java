package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.common.ApiResponse;
import com.zerobugfreinds.team_service.dto.CreateTeamRequest;
import com.zerobugfreinds.team_service.dto.InviteTeamMemberRequest;
import com.zerobugfreinds.team_service.dto.RegisterTeamApiKeyRequest;
import com.zerobugfreinds.team_service.dto.TeamApiKeySummaryResponse;
import com.zerobugfreinds.team_service.dto.UpdateTeamApiKeyRequest;
import com.zerobugfreinds.team_service.dto.TeamSummaryResponse;
import com.zerobugfreinds.team_service.security.TeamUserPrincipal;
import com.zerobugfreinds.team_service.service.TeamApiKeyService;
import com.zerobugfreinds.team_service.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TeamController {
	private final TeamService teamService;
	private final TeamApiKeyService teamApiKeyService;

	public TeamController(TeamService teamService, TeamApiKeyService teamApiKeyService) {
		this.teamService = teamService;
		this.teamApiKeyService = teamApiKeyService;
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

	@PostMapping("/teams/{id}/api-keys")
	public ResponseEntity<ApiResponse<TeamApiKeySummaryResponse>> registerTeamApiKey(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("id") Long teamId,
			@Valid @RequestBody RegisterTeamApiKeyRequest request
	) {
		TeamApiKeySummaryResponse created = teamApiKeyService.register(
				principal.userId(),
				teamId,
				request.provider(),
				request.alias(),
				request.externalKey(),
				request.monthlyBudgetUsd()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("팀 API 키가 등록되었습니다", created));
	}

	@PutMapping("/teams/{teamId}/api-keys/{keyId}")
	public ResponseEntity<ApiResponse<TeamApiKeySummaryResponse>> updateTeamApiKey(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("teamId") Long teamId,
			@PathVariable("keyId") Long keyId,
			@Valid @RequestBody UpdateTeamApiKeyRequest request
	) {
		TeamApiKeySummaryResponse updated = teamApiKeyService.update(
				principal.userId(),
				teamId,
				keyId,
				request.provider(),
				request.alias(),
				request.externalKey(),
				request.monthlyBudgetUsd()
		);
		return ResponseEntity.ok(ApiResponse.ok("팀 API 키가 수정되었습니다", updated));
	}

	@DeleteMapping("/teams/{teamId}/api-keys/{keyId}")
	public ResponseEntity<ApiResponse<Void>> deleteTeamApiKey(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("teamId") Long teamId,
			@PathVariable("keyId") Long keyId
	) {
		teamApiKeyService.delete(principal.userId(), teamId, keyId);
		return ResponseEntity.ok(ApiResponse.ok("팀 API 키가 삭제되었습니다", null));
	}

	@GetMapping("/teams/{id}/api-keys")
	public ResponseEntity<ApiResponse<List<TeamApiKeySummaryResponse>>> getTeamApiKeys(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("id") Long teamId
	) {
		List<TeamApiKeySummaryResponse> apiKeys = teamApiKeyService.getTeamApiKeys(principal.userId(), teamId);
		return ResponseEntity.ok(ApiResponse.ok("팀 API 키 목록 조회에 성공했습니다", apiKeys));
	}
}
