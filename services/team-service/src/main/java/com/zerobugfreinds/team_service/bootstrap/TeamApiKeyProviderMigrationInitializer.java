package com.zerobugfreinds.team_service.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 팀 API 키 provider 레거시 값(GEMINI)을 GOOGLE로 통일한다.
 */
@Component
public class TeamApiKeyProviderMigrationInitializer {
    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyProviderMigrationInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public TeamApiKeyProviderMigrationInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateGeminiProviderToGoogle() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where upper(table_name) = 'TEAM_API_KEYS'
                """,
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            log.info("team_api_keys table not found; skip provider migration");
            return;
        }

        int updated = jdbcTemplate.update(
                """
                update team_api_keys
                set provider = 'GOOGLE'
                where provider = 'GEMINI'
                """
        );
        log.info("team_api_keys provider migration completed updatedRows={}", updated);
    }
}
