package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.IdentityApiKeySnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/identity-api-keys")
public class IdentityApiKeySnapshotController {

	private final IdentityApiKeySnapshotService identityApiKeySnapshotService;

	public IdentityApiKeySnapshotController(IdentityApiKeySnapshotService identityApiKeySnapshotService) {
		this.identityApiKeySnapshotService = identityApiKeySnapshotService;
	}

	@GetMapping("/{userId}")
	public List<IdentityApiKeySnapshotService.ApiKeySnapshot> listByUserId(@PathVariable("userId") Long userId) {
		return identityApiKeySnapshotService.findByUserId(userId);
	}
}
