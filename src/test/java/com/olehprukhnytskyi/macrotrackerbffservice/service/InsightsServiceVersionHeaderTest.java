package com.olehprukhnytskyi.macrotrackerbffservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class InsightsServiceVersionHeaderTest {
    @Test
    void forwardsAndroidVersionWhenCheckingEntitlement() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ExchangeFunction entitlementExchange = request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"features\":{\"advancedInsights\":false}}")
                    .build());
        };
        WebClient unusedClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(
                        new AssertionError("Downstream data must not be fetched")))
                .build();
        InsightsService service = new InsightsService(
                WebClient.builder().exchangeFunction(entitlementExchange).build(),
                unusedClient,
                unusedClient);

        StepVerifier.create(service.getInsights(7L, "30d", "42"))
                .expectError(ResponseStatusException.class)
                .verify();

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().headers().getFirst("X-App-Version-Code"))
                .isEqualTo("42");
    }
}
