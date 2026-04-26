package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.common.ApiResponse;
import com.zerobugfreinds.team_service.dto.CreateTeamRequest;
import com.zerobugfreinds.team_service.dto.InviteTeamMemberRequest;
import com.zerobugfreinds.team_service.dto.RegisterTeamApiKeyRequest;
import com.zerobugfreinds.team_service.dto.TeamApiKeySummaryResponse;
import com.zerobugfreinds.team_service.dto.TeamInvitationActionResponse;
import com.zerobugfreinds.team_service.dto.TeamInvitationResponse;
import com.zerobugfreinds.team_service.dto.TeamResponse;
import com.zerobugfreinds.team_service.dto.UpdateTeamApiKeyRequest;
import com.zerobugfreinds.team_service.dto.TeamSummaryResponse;
import com.zerobugfreinds.team_service.security.TeamUserPrincipal;
import com.zerobugfreinds.team_service.service.TeamApiKeyService;
import com.zerobugfreinds.team_service.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
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

	@GetMapping("/teams")
	public ResponseEntity<ApiResponse<Page<TeamResponse>>> getTeams(
			@RequestParam(name = "keyword", required = false) String keyword,
			@PageableDefault(size = 20) Pageable pageable
	) {
		Page<TeamResponse> teams = teamService.getTeams(keyword, pageable);
		return ResponseEntity.ok(ApiResponse.ok("팀 검색에 성공했습니다", teams));
	}

	@PostMapping("/teams/{id}/members")
	public ResponseEntity<ApiResponse<TeamSummaryResponse>> inviteMember(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("id") Long teamId,
			@Valid @RequestBody InviteTeamMemberRequest request
	) {
		TeamSummaryResponse team = teamService.inviteMember(principal.userId(), teamId, request.userId().trim());
		return ResponseEntity.ok(ApiResponse.ok("팀 초대를 보냈습니다", team));
	}

	@GetMapping("/me/team-invitations")
	public ResponseEntity<ApiResponse<List<TeamInvitationResponse>>> getMyPendingInvitations(
			@AuthenticationPrincipal TeamUserPrincipal principal
	) {
		List<TeamInvitationResponse> invitations = teamService.getMyPendingInvitations(principal.userId());
		return ResponseEntity.ok(ApiResponse.ok("내 팀 초대 목록 조회에 성공했습니다", invitations));
	}

	@PostMapping("/me/team-invitations/{invitationId}/accept")
	public ResponseEntity<ApiResponse<TeamInvitationActionResponse>> acceptInvitation(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("invitationId") Long invitationId
	) {
		TeamInvitationActionResponse result = teamService.acceptInvitation(principal.userId(), invitationId);
		return ResponseEntity.ok(ApiResponse.ok("팀 초대를 수락했습니다", result));
	}

	@PostMapping("/me/team-invitations/{invitationId}/reject")
	public ResponseEntity<ApiResponse<TeamInvitationActionResponse>> rejectInvitation(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("invitationId") Long invitationId
	) {
		TeamInvitationActionResponse result = teamService.rejectInvitation(principal.userId(), invitationId);
		return ResponseEntity.ok(ApiResponse.ok("팀 초대를 거절했습니다", result));
	}

	@GetMapping("/teams/{id}/members")
	public ResponseEntity<ApiResponse<List<String>>> getTeamMembers(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("id") Long teamId
	) {
		List<String> members = teamService.getTeamMemberUserIds(principal.userId(), teamId);
		return ResponseEntity.ok(ApiResponse.ok("팀 멤버 조회에 성공했습니다", members));
	}

	@GetMapping("/teams/{id}/owner")
	public ResponseEntity<ApiResponse<Boolean>> getIsTeamOwner(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("id") Long teamId
	) {
		boolean owner = teamService.isTeamOwner(principal.userId(), teamId);
		return ResponseEntity.ok(ApiResponse.ok("팀장 여부 조회에 성공했습니다", owner));
	}

	@DeleteMapping("/teams/{teamId}/members/{userId}")
	public ResponseEntity<ApiResponse<TeamSummaryResponse>> removeTeamMember(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("teamId") Long teamId,
			@PathVariable("userId") String userId
	) {
		TeamSummaryResponse team = teamService.removeMember(principal.userId(), teamId, userId.trim());
		return ResponseEntity.ok(ApiResponse.ok("팀원이 삭제되었습니다", team));
	}

	@DeleteMapping("/teams/{teamId}")
	public ResponseEntity<ApiResponse<Void>> deleteTeam(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("teamId") Long teamId
	) {
		teamService.deleteTeam(principal.userId(), teamId);
		return ResponseEntity.ok(ApiResponse.ok("팀이 삭제되었습니다", null));
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
	public ResponseEntity<ApiResponse<TeamApiKeySummaryResponse>> deleteTeamApiKey(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("teamId") Long teamId,
			@PathVariable("keyId") Long keyId,
			@RequestParam(name = "gracePeriodDays", required = false) Integer gracePeriodDays,
			@RequestParam(name = "retainLogs", defaultValue = "true") boolean retainLogs
	) {
		TeamApiKeySummaryResponse updated =
				teamApiKeyService.delete(principal.userId(), teamId, keyId, gracePeriodDays, retainLogs);
		return ResponseEntity.ok(ApiResponse.ok("팀 API 키 삭제 요청이 처리되었습니다", updated));
	}

	@PostMapping("/teams/{teamId}/api-keys/{keyId}/deletion/cancel")
	public ResponseEntity<ApiResponse<TeamApiKeySummaryResponse>> cancelTeamApiKeyDeletion(
			@AuthenticationPrincipal TeamUserPrincipal principal,
			@PathVariable("teamId") Long teamId,
			@PathVariable("keyId") Long keyId
	) {
		TeamApiKeySummaryResponse updated = teamApiKeyService.cancelDeletion(principal.userId(), teamId, keyId);
		return ResponseEntity.ok(ApiResponse.ok("삭제 예정이 해제되었습니다", updated));
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
