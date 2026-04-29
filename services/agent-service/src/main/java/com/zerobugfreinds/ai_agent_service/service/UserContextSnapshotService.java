package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.identity.events.UserContextChangedEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserContextSnapshotService {

	private final Map<Long, UserContextSnapshot> snapshotsByUserId = new ConcurrentHashMap<>();

	public void upsert(UserContextChangedEvent event) {
		Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
		snapshotsByUserId.put(
				event.userId(),
				new UserContextSnapshot(
						event.userId(),
						event.activeTeamId(),
						event.role(),
						updatedAt
				)
		);
	}

	public List<UserContextSnapshot> findAll() {
		return snapshotsByUserId.values().stream()
				.sorted(Comparator.comparing(UserContextSnapshot::updatedAt).reversed())
				.toList();
	}

	public record UserContextSnapshot(
			Long userId,
			Long activeTeamId,
			String role,
			Instant updatedAt
	) {
	}
}
