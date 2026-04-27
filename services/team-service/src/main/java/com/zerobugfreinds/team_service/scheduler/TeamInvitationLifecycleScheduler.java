package com.zerobugfreinds.team_service.scheduler;

import com.zerobugfreinds.team_service.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TeamInvitationLifecycleScheduler {

	private static final Logger log = LoggerFactory.getLogger(TeamInvitationLifecycleScheduler.class);

	private final TeamService teamService;

	public TeamInvitationLifecycleScheduler(TeamService teamService) {
		this.teamService = teamService;
	}

	@Scheduled(
			fixedDelayString = "${team.invitation.lifecycle-fixed-delay-ms:3600000}",
			initialDelayString = "${team.invitation.lifecycle-initial-delay-ms:60000}"
	)
	public void processInvitationLifecycle() {
		int expired = teamService.expireStaleInvitations();
		int purged = teamService.purgeOldInvitations();
		if (expired > 0 || purged > 0) {
			log.info("team_invitation lifecycle processed expired={} purged={}", expired, purged);
		}
	}
}
