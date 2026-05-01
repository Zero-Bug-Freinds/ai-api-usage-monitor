package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.UserContextSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/user-contexts")
public class UserContextSnapshotController {

	private final UserContextSnapshotService snapshotService;

	public UserContextSnapshotController(UserContextSnapshotService snapshotService) {
		this.snapshotService = snapshotService;
	}

	@GetMapping
	public List<UserContextSnapshotService.UserContextSnapshot> list() {
		return snapshotService.findAll();
	}
}
