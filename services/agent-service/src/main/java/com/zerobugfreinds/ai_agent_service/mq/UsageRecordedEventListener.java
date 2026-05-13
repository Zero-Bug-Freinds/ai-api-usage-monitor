package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.UsageRecordedEvent;

/**
 * Usage 이벤트에서 billing·예측 스냅샷용 scope (개인 vs 팀)을 도출한다.
 * <p>Proxy reverse-lookup 경로에서는 {@code apiKeySource} 가 {@code "team"} 이 아니어도 팀 식별자가
 * 채워질 수 있으므로, 팀 ID·팀 키 식별자를 우선한다.</p>
 */
public final class UsageRecordedEventListener {

	private UsageRecordedEventListener() {
	}

	public record ResolvedScope(String scopeType, String scopeId) {
	}

	/**
	 * @return {@code PERSONAL} / {@code TEAM} 과 해당 scope id, 또는 식별 불가 시 {@code null}
	 */
	public static ResolvedScope resolveScope(UsageRecordedEvent event) {
		if (event == null) {
			return null;
		}
		String userId = trimToNull(event.userId());
		String teamId = trimToNull(event.teamId());
		String teamKeyId = trimToNull(event.apiKeyFingerprint());
		String apiKeySource = trimToNull(event.apiKeySource());

		if (apiKeySource == null) {
			return null;
		}

		if ("team".equals(apiKeySource)) {
			if (teamId != null) {
				return new ResolvedScope("TEAM", teamId);
			}
			return null;
		}

		if ("reverse_lookup".equals(apiKeySource)) {
			if (teamId != null) {
				return new ResolvedScope("TEAM", teamId);
			}
			if (teamKeyId != null) {
				return new ResolvedScope("TEAM", teamKeyId);
			}
			return null;
		}

		if ("managed".equals(apiKeySource)) {
			if (userId == null) {
				return null;
			}
			return new ResolvedScope("PERSONAL", userId);
		}

		return null;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
