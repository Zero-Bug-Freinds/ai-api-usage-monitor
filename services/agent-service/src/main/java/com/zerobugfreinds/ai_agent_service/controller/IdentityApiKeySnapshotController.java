package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.IdentityApiKeySnapshotService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/identity-api-keys")
public class IdentityApiKeySnapshotController {

	private final IdentityApiKeySnapshotService identityApiKeySnapshotService;

	public IdentityApiKeySnapshotController(IdentityApiKeySnapshotService identityApiKeySnapshotService) {
		this.identityApiKeySnapshotService = identityApiKeySnapshotService;
	}

	/**
	 * 전체 스냅샷 조회는 사용하지 않는다. {@code X-User-Email}(또는 이메일 형식 path)로 소유자를 한정한다.
	 */
	@GetMapping
	public List<IdentityApiKeySnapshotService.ApiKeySnapshot> listAll(
			@RequestHeader(value = "X-User-Email", required = false) String userEmail
	) {
		if (!StringUtils.hasText(userEmail) || !userEmail.contains("@")) {
			return List.of();
		}
		return identityApiKeySnapshotService.findByUserId(userEmail.trim().toLowerCase(Locale.ROOT));
	}

	@GetMapping("/{userId}")
	public List<IdentityApiKeySnapshotService.ApiKeySnapshot> listByUserId(@PathVariable("userId") String userId) {
		return identityApiKeySnapshotService.findByUserId(userId);
	}
}
