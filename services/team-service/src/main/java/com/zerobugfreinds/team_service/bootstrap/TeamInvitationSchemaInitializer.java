package com.zerobugfreinds.team_service.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 팀 초대 상태 CHECK 제약을 현행 enum(PENDING/ACCEPTED/REJECTED/EXPIRED)에 맞춘다.
 */
@Component
public class TeamInvitationSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TeamInvitationSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public TeamInvitationSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.update("ALTER TABLE team_invitations DROP CONSTRAINT IF EXISTS team_invitations_status_check");
        jdbcTemplate.update(
                "ALTER TABLE team_invitations " +
                        "ADD CONSTRAINT team_invitations_status_check " +
                        "CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED'))"
        );
        log.info("team_invitations.status check constraint ensured with EXPIRED");
    }
}
