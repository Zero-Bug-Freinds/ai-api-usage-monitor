package com.zerobugfreinds.ai_agent_service.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TeamSnapshotService {

	private final Map<Long, TeamSnapshot> snapshotsByTeamId = new ConcurrentHashMap<>();

	public void upsert(TeamSnapshot snapshot) {
		snapshotsByTeamId.put(snapshot.teamId(), snapshot);
	}

	public void remove(Long teamId) {
		if (teamId == null) {
			return;
		}
		snapshotsByTeamId.remove(teamId);
	}

	public List<TeamSnapshot> findAll() {
		return snapshotsByTeamId.values().stream()
				.sorted(Comparator.comparing(TeamSnapshot::updatedAt).reversed())
				.toList();
	}

	public record TeamSnapshot(
			Long teamId,
			String teamName,
			Instant updatedAt
	) {
	}
}
