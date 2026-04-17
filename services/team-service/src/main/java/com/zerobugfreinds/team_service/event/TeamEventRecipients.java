package com.zerobugfreinds.team_service.event;

import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

public final class TeamEventRecipients {

	private TeamEventRecipients() {
	}

	public static List<String> allMemberUserIds(TeamMemberRepository teamMemberRepository, Long teamId) {
		LinkedHashSet<String> out = new LinkedHashSet<>();
		for (var m : teamMemberRepository.findAllByTeamId(teamId)) {
			if (StringUtils.hasText(m.getUserId())) {
				out.add(m.getUserId().trim());
			}
		}
		return List.copyOf(out);
	}
}
