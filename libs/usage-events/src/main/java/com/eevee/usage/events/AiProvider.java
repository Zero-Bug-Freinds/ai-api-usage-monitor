package com.eevee.usage.events;

/**
 * Proxy path segment and usage event discriminator for AI providers.
 */
public enum AiProvider {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    GOOGLE("google");

    private final String pathSegment;

    AiProvider(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String pathSegment() {
        return pathSegment;
    }

    public static AiProvider fromPathSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("provider segment is required");
        }
        String normalized = segment.trim().toLowerCase();
        for (AiProvider p : values()) {
            if (p.pathSegment.equals(normalized)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unknown provider: " + segment);
    }
}
