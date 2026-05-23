package com.eevee.proxyservice.relay;

import com.eevee.proxyservice.key.ApiKeyClient;
import com.eevee.proxyservice.mq.UsageEventPublisher;
import com.eevee.proxyservice.provider.ProviderRegistry;
import com.eevee.proxyservice.security.UserContext;
import com.eevee.proxyservice.security.UserContextResolver;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.UsageRecordedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProxyRelayServiceUsageEventTest {

    @Test
    void publishUsage_usesResolvedEmailUsageSubjectUserId() {
        UsageEventPublisher publisher = mock(UsageEventPublisher.class);
        AtomicReference<UsageRecordedEvent> captured = new AtomicReference<>();
        when(publisher.publish(any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return Mono.empty();
        });

        ProxyRelayService service = new ProxyRelayService(
                WebClient.create(),
                mock(ProviderRegistry.class),
                mock(ApiKeyClient.class),
                publisher,
                mock(UserContextResolver.class)
        );

        UserContext ctx = new UserContext(
                null,
                null,
                null,
                null,
                "corr-1",
                null,
                null,
                null,
                null,
                null
        );
        ApiKeyClient.ResolvedApiKey resolved = new ApiKeyClient.ResolvedApiKey(
                "sk-plain",
                "key-1",
                null,
                "alias",
                "fp",
                "managed",
                "user@test.com",
                null
        );

        Mono<Void> published = invokePublishUsage(service, ctx, resolved);
        StepVerifier.create(published).verifyComplete();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().userId()).isEqualTo("user@test.com");
    }

    @Test
    void publishUsage_managedJwtPath_keepsGatewayEmail_notLookupPk() {
        UsageEventPublisher publisher = mock(UsageEventPublisher.class);
        AtomicReference<UsageRecordedEvent> captured = new AtomicReference<>();
        when(publisher.publish(any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return Mono.empty();
        });

        ProxyRelayService service = new ProxyRelayService(
                WebClient.create(),
                mock(ProviderRegistry.class),
                mock(ApiKeyClient.class),
                publisher,
                mock(UserContextResolver.class)
        );

        UserContext ctx = new UserContext(
                "mmin08@naver.com",
                "3",
                null,
                null,
                "corr-2",
                "42",
                null,
                null,
                null,
                null
        );
        ApiKeyClient.ResolvedApiKey resolved = new ApiKeyClient.ResolvedApiKey(
                "sk-plain",
                "42",
                null,
                "alias",
                "fp",
                "managed",
                null,
                null
        );

        Mono<Void> published = invokePublishUsage(service, ctx, resolved);
        StepVerifier.create(published).verifyComplete();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().userId()).isEqualTo("mmin08@naver.com");
        assertThat(captured.get().metadataOwnerUserId()).isEqualTo("3");
    }

    private static Mono<Void> invokePublishUsage(
            ProxyRelayService service,
            UserContext ctx,
            ApiKeyClient.ResolvedApiKey resolved
    ) {
        try {
            var enrichMethod = ProxyRelayService.class.getDeclaredMethod(
                    "enrichContext",
                    UserContext.class,
                    ApiKeyClient.ResolvedApiKey.class
            );
            enrichMethod.setAccessible(true);
            UserContext enriched = (UserContext) enrichMethod.invoke(null, ctx, resolved);

            var publishMethod = ProxyRelayService.class.getDeclaredMethod(
                    "publishUsage",
                    UserContext.class,
                    AiProvider.class,
                    ApiKeyClient.ResolvedApiKey.class,
                    String.class,
                    com.eevee.usage.events.TokenUsage.class,
                    String.class,
                    Long.class,
                    boolean.class,
                    org.springframework.http.HttpStatusCode.class
            );
            publishMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            Mono<Void> published = (Mono<Void>) publishMethod.invoke(
                    service,
                    enriched,
                    AiProvider.OPENAI,
                    resolved,
                    "/proxy/openai/v1/chat/completions",
                    null,
                    "api.openai.com",
                    120L,
                    false,
                    org.springframework.http.HttpStatus.OK
            );
            return published;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
