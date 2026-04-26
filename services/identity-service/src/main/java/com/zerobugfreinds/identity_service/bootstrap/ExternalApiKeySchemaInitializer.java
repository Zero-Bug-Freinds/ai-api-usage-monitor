package com.zerobugfreinds.identity_service.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Flyway 없이 ddl-auto=update를 사용하는 로컬 개발환경에서
 * external_api_keys.retain_usage_logs 컬럼을 안전하게 보정한다.
 */
@Component
public class ExternalApiKeySchemaInitializer {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeySchemaInitializer.class);

	private final JdbcTemplate jdbcTemplate;

	public ExternalApiKeySchemaInitializer(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void ensureRetainUsageLogsColumn() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from information_schema.tables
				where upper(table_name) = 'EXTERNAL_API_KEYS'
				""",
				Integer.class
		);
		if (tableCount == null || tableCount == 0) {
			log.info("external_api_keys table not found; skip retain_usage_logs schema initialization");
			return;
		}

		jdbcTemplate.execute(
				"""
				alter table if exists external_api_keys
				add column if not exists retain_usage_logs boolean
				"""
		);
		jdbcTemplate.execute(
				"""
				update external_api_keys
				set retain_usage_logs = true
				where retain_usage_logs is null
				"""
		);
		jdbcTemplate.execute(
				"""
				alter table if exists external_api_keys
				alter column retain_usage_logs set default true
				"""
		);
		jdbcTemplate.execute(
				"""
				alter table if exists external_api_keys
				alter column retain_usage_logs set not null
				"""
		);
		log.info("external_api_keys.retain_usage_logs column ensured");
	}
}
