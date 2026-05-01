package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.TeamApiKeySnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/team-api-keys")
public class TeamApiKeySnapshotController {

	private final TeamApiKeySnapshotService snapshotService;

	public TeamApiKeySnapshotController(TeamApiKeySnapshotService snapshotService) {
		this.snapshotService = snapshotService;
	}

	@GetMapping
	public List<TeamApiKeySnapshotService.TeamApiKeySnapshot> list(
			@RequestParam(name = "teamId", required = false) Long teamId
	) {
		if (teamId != null) {
			return snapshotService.findByTeamId(teamId);
		}
		return snapshotService.findAll();
	}
}
