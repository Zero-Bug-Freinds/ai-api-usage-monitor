package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.TeamSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/teams")
public class TeamSnapshotController {

	private final TeamSnapshotService snapshotService;

	public TeamSnapshotController(TeamSnapshotService snapshotService) {
		this.snapshotService = snapshotService;
	}

	@GetMapping
	public List<TeamSnapshotService.TeamSnapshot> list() {
		return snapshotService.findAll();
	}
}
