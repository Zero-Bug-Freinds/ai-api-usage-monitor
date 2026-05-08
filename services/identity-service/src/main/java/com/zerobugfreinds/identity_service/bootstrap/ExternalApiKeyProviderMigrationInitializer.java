package com.zerobugfreinds.identity_service.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 외부 API 키 provider 레거시 값(GEMINI)을 GOOGLE로 통일한다.
 */
@Component
public class ExternalApiKeyProviderMigrationInitializer {
	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyProviderMigrationInitializer.class);

	private final JdbcTemplate jdbcTemplate;

	public ExternalApiKeyProviderMigrationInitializer(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void migrateGeminiProviderToGoogle() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from information_schema.tables
				where upper(table_name) = 'EXTERNAL_API_KEYS'
				""",
				Integer.class
		);
		if (tableCount == null || tableCount == 0) {
			log.info("external_api_keys table not found; skip provider migration");
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
				    where t.relname = 'external_api_keys'
				      and c.conname = 'external_api_keys_provider_check'
				  ) then
				    alter table external_api_keys
				      drop constraint external_api_keys_provider_check;
				  end if;
				end $$;
				"""
		);
		jdbcTemplate.execute(
				"""
				alter table external_api_keys
				add constraint external_api_keys_provider_check
				check (provider in ('GEMINI', 'GOOGLE', 'OPENAI', 'ANTHROPIC'))
				"""
		);

		int updated = jdbcTemplate.update(
				"""
				update external_api_keys
				set provider = 'GOOGLE'
				where cast(provider as varchar) = 'GEMINI'
				"""
		);
		log.info("external_api_keys provider migration completed updatedRows={}", updated);
	}
}
