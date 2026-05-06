package com.zerobugfreinds.identity_service.domain;

/**
 * 사용자가 등록하는 외부 AI API 제공자.
 */
public enum ExternalApiKeyProvider {
	GEMINI,
	GOOGLE,
	OPENAI,
	ANTHROPIC,
	META,
	MISTRAL,
	COHERE,
	GROK;

	public static ExternalApiKeyProvider fromInternalPathSegment(String segment) {
		if (segment == null || segment.isBlank()) {
			throw new IllegalArgumentException("provider segment is required");
		}
		String normalized = segment.trim().toLowerCase();
		return switch (normalized) {
			case "openai" -> OPENAI;
			case "anthropic" -> ANTHROPIC;
			case "google" -> GOOGLE;
			case "gemini" -> GEMINI;
			case "meta" -> META;
			case "mistral" -> MISTRAL;
			case "cohere" -> COHERE;
			case "grok" -> GROK;
			default -> throw new IllegalArgumentException("unknown provider: " + segment);
		};
	}
}
