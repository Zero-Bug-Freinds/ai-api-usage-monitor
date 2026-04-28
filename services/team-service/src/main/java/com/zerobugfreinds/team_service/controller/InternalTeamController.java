package com.zerobugfreinds.team_service.controller;

import com.zerobugfreinds.team_service.common.ApiResponse;
import com.zerobugfreinds.team_service.dto.InternalBillingTeamSummaryResponse;
import com.zerobugfreinds.team_service.dto.InternalTeamInvitationDecisionRequest;
import com.zerobugfreinds.team_service.dto.InternalTeamMembershipVerifyResponse;
import com.zerobugfreinds.team_service.dto.InternalTeamDetailResponse;
import com.zerobugfreinds.team_service.dto.TeamInvitationActionResponse;
import com.zerobugfreinds.team_service.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내부 서비스 간 호출용 팀 조회 API.
 * 외부 Gateway 라우트에서는 노출하지 않는 Internal 전용 엔드포인트다.
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

	@GetMapping("/teams/users/{userId}/billing-summaries")
	public ResponseEntity<ApiResponse<List<InternalBillingTeamSummaryResponse>>> getBillingTeamSummaries(
			@PathVariable("userId") String userId
	) {
		List<InternalBillingTeamSummaryResponse> summaries = teamService.getBillingTeamSummariesInternal(userId);
		return ResponseEntity.ok(ApiResponse.ok("Billing용 팀 목록 조회에 성공했습니다", summaries));
	}

	@GetMapping("/v1/teams/{teamId}/members/{userId}/verify")
	public ResponseEntity<ApiResponse<InternalTeamMembershipVerifyResponse>> verifyTeamMembership(
			@PathVariable("teamId") Long teamId,
			@PathVariable("userId") String userId
	) {
		InternalTeamMembershipVerifyResponse response = teamService.verifyTeamMembershipInternal(teamId, userId);
		return ResponseEntity.ok(ApiResponse.ok("팀 멤버십 검증에 성공했습니다", response));
	}

	@PostMapping("/v1/team-invitations/{invitationId}/decision")
	public ResponseEntity<ApiResponse<TeamInvitationActionResponse>> processInvitationDecision(
			@PathVariable("invitationId") Long invitationId,
			@Valid @RequestBody InternalTeamInvitationDecisionRequest request
	) {
		TeamInvitationActionResponse result = teamService.processInvitationDecisionInternal(
				request.inviteeUserId().trim(),
				invitationId,
				request.decision()
		);
		String message = request.decision() == InternalTeamInvitationDecisionRequest.Decision.ACCEPT
				? "팀 초대를 수락했습니다"
				: "팀 초대를 거절했습니다";
		return ResponseEntity.ok(ApiResponse.ok(message, result));
	}
}
