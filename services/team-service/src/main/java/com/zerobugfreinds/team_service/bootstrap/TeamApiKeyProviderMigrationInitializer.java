package com.zerobugfreinds.team_service.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 팀 API 키 provider 레거시 값을 현재 enum/계약과 호환되도록 보정한다.
 */
@Component
public class TeamApiKeyProviderMigrationInitializer {
    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyProviderMigrationInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public TeamApiKeyProviderMigrationInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateProviders() {
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

        boolean postgres = isPostgres();
        if (postgres) {
            dropProviderCheckConstraint();
        }

        int updatedGemini = jdbcTemplate.update(
                """
                update team_api_keys
                set provider = 'GOOGLE'
                where cast(provider as varchar) = 'GEMINI'
                """
        );
        if (postgres) {
            addProviderCheckConstraint();
        }
        log.info("team_api_keys provider migration completed updatedGeminiRows={}", updatedGemini);
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")
        ));
    }

    private void dropProviderCheckConstraint() {
        jdbcTemplate.execute(
                """
                alter table team_api_keys
                drop constraint if exists team_api_keys_provider_check
                """
        );
    }

    private void addProviderCheckConstraint() {
        jdbcTemplate.execute(
                """
                alter table team_api_keys
                add constraint team_api_keys_provider_check
                check (provider in ('OPENAI', 'GOOGLE', 'ANTHROPIC', 'CLAUDE', 'META', 'MISTRAL', 'COHERE', 'GROK'))
                """
        );
    }
}
