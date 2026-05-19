package com.eevee.apigateway.util;

import com.eevee.usage.events.AiProvider;

/**
 * Parses provider path segment from {@code /api/v1/ai/ext/{provider}/...}.
 */
public final class ExtAiProviderPath {

    public static final String EXT_PREFIX = "/api/v1/ai/ext/";

    private ExtAiProviderPath() {
    }

    public static boolean isExtAiPath(String path) {
        return path != null && path.startsWith(EXT_PREFIX);
    }

    /**
     * @return enum name (OPENAI, GOOGLE, ANTHROPIC) for downstream headers
     */
    public static String providerEnumNameFromPath(String path) {
        AiProvider provider = AiProvider.fromPathSegment(providerSegmentFromPath(path));
        return provider.name();
    }

    public static String providerSegmentFromPath(String path) {
        if (!isExtAiPath(path)) {
            throw new IllegalArgumentException("not an ext ai path: " + path);
        }
        String remainder = path.substring(EXT_PREFIX.length());
        int slash = remainder.indexOf('/');
        String segment = slash >= 0 ? remainder.substring(0, slash) : remainder;
        if (segment.isBlank()) {
            throw new IllegalArgumentException("missing provider segment in ext path");
        }
        return segment.trim();
    }
}
