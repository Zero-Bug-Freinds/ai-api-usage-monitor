package com.eevee.proxygateway.provider;

import com.eevee.usage.events.AiProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderRegistry {

    private final Map<AiProvider, ProviderHandler> handlers = new EnumMap<>(AiProvider.class);

    public ProviderRegistry(List<ProviderHandler> handlerList) {
        for (ProviderHandler h : handlerList) {
            handlers.put(h.provider(), h);
        }
    }

    public ProviderHandler get(AiProvider provider) {
        ProviderHandler h = handlers.get(provider);
        if (h == null) {
            throw new IllegalArgumentException("No handler for provider: " + provider);
        }
        return h;
    }
}
