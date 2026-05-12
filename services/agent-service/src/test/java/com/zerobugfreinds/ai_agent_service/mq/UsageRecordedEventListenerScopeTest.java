package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UsageRecordedEventListener#resolveScope(UsageRecordedEvent)} 의 분기 보정 검증.
 *
 * <p>핵심 회귀 시나리오는 proxy 의 reverse-lookup 경로다. 이 경우 {@code apiKeySource} 가
 * {@code "team"} 이 아닌 다른 값으로 들어와도 {@code teamApiKeyId}/{@code teamId} 가 채워지므로
 * scope 은 TEAM 으로 분류되어야 한다. 이 테스트는 이전 구현(=apiKeySource 만 보고 PERSONAL 로 떨어뜨리던
 * 동작)을 복구하지 못하도록 막는다.</p>
 */
class UsageRecordedEventListenerScopeTest {

	@Test
	void teamSourceWithTeamIdResolvesToTeamScope() {
		UsageRecordedEvent event = newEvent("user-1", "team-9", "team-key-77", "team");

		UsageRecordedEventListener.ResolvedScope resolved =
				UsageRecordedEventListener.resolveScope(event);

		assertThat(resolved).isNotNull();
		assertThat(resolved.scopeType()).isEqualTo("TEAM");
		assertThat(resolved.scopeId()).isEqualTo("team-9");
	}

	@Test
	void reverseLookupSourceWithTeamIdResolvesToTeamScope() {
		UsageRecordedEvent event = newEvent("user-1", "team-9", "team-key-77", "reverse_lookup");

		UsageRecordedEventListener.ResolvedScope resolved =
				UsageRecordedEventListener.resolveScope(event);

		assertThat(resolved).isNotNull();
		assertThat(resolved.scopeType()).isEqualTo("TEAM");
		assertThat(resolved.scopeId()).isEqualTo("team-9");
	}

	@Test
	void teamApiKeyIdWithoutTeamIdStillResolvesToTeamScope() {
		UsageRecordedEvent event = newEvent("user-1", null, "team-key-77", "reverse_lookup");

		UsageRecordedEventListener.ResolvedScope resolved =
				UsageRecordedEventListener.resolveScope(event);

		assertThat(resolved).isNotNull();
		assertThat(resolved.scopeType()).isEqualTo("TEAM");
		assertThat(resolved.scopeId()).isEqualTo("team-key-77");
	}

	@Test
	void managedSourceWithUserIdResolvesToPersonalScope() {
		UsageRecordedEvent event = newEvent("user-1", null, null, "managed");

		UsageRecordedEventListener.ResolvedScope resolved =
				UsageRecordedEventListener.resolveScope(event);

		assertThat(resolved).isNotNull();
		assertThat(resolved.scopeType()).isEqualTo("PERSONAL");
		assertThat(resolved.scopeId()).isEqualTo("user-1");
	}

	@Test
	void teamSourceWithoutAnyTeamIdReturnsNull() {
		UsageRecordedEvent event = newEvent("user-1", null, null, "team");

		UsageRecordedEventListener.ResolvedScope resolved =
				UsageRecordedEventListener.resolveScope(event);

		assertThat(resolved).isNull();
	}

	@Test
	void blankUserIdAndNoTeamIdentifiersReturnsNull() {
		UsageRecordedEvent event = newEvent("   ", null, null, "managed");

		UsageRecordedEventListener.ResolvedScope resolved =
				UsageRecordedEventListener.resolveScope(event);

		assertThat(resolved).isNull();
	}

	@Test
	void blankTeamIdentifiersFallBackToPersonalScope() {
		UsageRecordedEvent event = newEvent("user-1", "  ", "  ", "managed");

		UsageRecordedEventListener.ResolvedScope resolved =
				UsageRecordedEventListener.resolveScope(event);

		assertThat(resolved).isNotNull();
		assertThat(resolved.scopeType()).isEqualTo("PERSONAL");
		assertThat(resolved.scopeId()).isEqualTo("user-1");
	}

	private static UsageRecordedEvent newEvent(
			String userId,
			String teamId,
			String teamApiKeyId,
			String apiKeySource
	) {
		return new UsageRecordedEvent(
				UUID.randomUUID(),
				Instant.now(),
				"corr-1",
				userId,
				"org-1",
				teamId,
				"key-1",
				"alias",
				teamApiKeyId,
				"fingerprint",
				apiKeySource,
				AiProvider.OPENAI,
				"gpt-4o",
				new TokenUsage("gpt-4o", 10L, 5L, 15L, null, null, null, null, null, null),
				BigDecimal.ZERO,
				"/v1/chat/completions",
				"api.openai.com",
				120L,
				Boolean.FALSE,
				Boolean.TRUE,
				200
		);
	}
}