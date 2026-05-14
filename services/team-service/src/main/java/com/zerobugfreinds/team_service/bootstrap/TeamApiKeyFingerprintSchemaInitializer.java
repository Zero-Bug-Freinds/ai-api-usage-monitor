package com.zerobugfreinds.team_service.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * team_api_keys.api_key_fingerprint 및 (provider, fingerprint) 부분 유일 인덱스 보정.
 */
@Component
public class TeamApiKeyFingerprintSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyFingerprintSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public TeamApiKeyFingerprintSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureApiKeyFingerprintColumnAndIndex() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where upper(table_name) = 'TEAM_API_KEYS'
                """,
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            log.info("team_api_keys table not found; skip api_key_fingerprint schema initialization");
            return;
        }
        jdbcTemplate.execute(
                """
                alter table if exists team_api_keys
                add column if not exists api_key_fingerprint varchar(64)
                """
        );
        if (isPostgres()) {
            jdbcTemplate.execute(
                    """
                    create unique index if not exists uk_team_api_keys_provider_fingerprint
                    on team_api_keys (provider, api_key_fingerprint)
                    where api_key_fingerprint is not null
                    """
            );
        }
        log.info("team_api_keys.api_key_fingerprint column ensured");
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")
        ));
    }
}
