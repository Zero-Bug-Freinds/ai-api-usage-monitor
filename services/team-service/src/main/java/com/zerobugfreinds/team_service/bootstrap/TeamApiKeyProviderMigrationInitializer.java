package com.zerobugfreinds.team_service.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

        jdbcTemplate.execute(
                """
                do $$
                begin
                  if exists (
                    select 1
                    from pg_constraint c
                    join pg_class t on c.conrelid = t.oid
                    where t.relname = 'team_api_keys'
                      and c.conname = 'team_api_keys_provider_check'
                  ) then
                    alter table team_api_keys
                      drop constraint team_api_keys_provider_check;
                  end if;
                end $$;
                """
        );
        jdbcTemplate.execute(
                """
                alter table team_api_keys
                add constraint team_api_keys_provider_check
                check (provider in ('OPENAI', 'GEMINI', 'GOOGLE', 'ANTHROPIC', 'CLAUDE', 'META', 'MISTRAL', 'COHERE', 'GROK'))
                """
        );

        int updatedGemini = jdbcTemplate.update(
                """
                update team_api_keys
                set provider = 'GOOGLE'
                where provider = 'GEMINI'
                """
        );
        log.info("team_api_keys provider migration completed updatedGeminiRows={}", updatedGemini);
    }
}
