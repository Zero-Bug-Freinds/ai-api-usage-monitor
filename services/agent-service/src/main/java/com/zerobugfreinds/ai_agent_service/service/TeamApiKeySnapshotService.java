package com.zerobugfreinds.ai_agent_service.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TeamApiKeySnapshotService {

	private final Map<Long, Map<Long, TeamApiKeySnapshot>> snapshotsByTeamId = new ConcurrentHashMap<>();

	public void upsert(TeamApiKeySnapshot snapshot) {
		Map<Long, TeamApiKeySnapshot> teamKeys = snapshotsByTeamId.computeIfAbsent(
				snapshot.teamId(),
				unused -> new ConcurrentHashMap<>()
		);
		teamKeys.put(snapshot.teamApiKeyId(), snapshot);
	}

	public List<TeamApiKeySnapshot> findByTeamId(Long teamId) {
		Map<Long, TeamApiKeySnapshot> teamKeys = snapshotsByTeamId.get(teamId);
		if (teamKeys == null) {
			return List.of();
		}
		return teamKeys.values().stream()
				.sorted(Comparator.comparing(TeamApiKeySnapshot::updatedAt).reversed())
				.toList();
	}

	public List<TeamApiKeySnapshot> findAll() {
		return snapshotsByTeamId.values().stream()
				.flatMap(v -> v.values().stream())
				.sorted(Comparator.comparing(TeamApiKeySnapshot::updatedAt).reversed())
				.toList();
	}

	public record TeamApiKeySnapshot(
			Long teamId,
			Long teamApiKeyId,
			String ownerUserId,
			String visibility,
			String alias,
			String provider,
			String status,
			Boolean retainLogs,
			Instant updatedAt
	) {
	}
}
