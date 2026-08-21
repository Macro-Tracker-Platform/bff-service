package com.olehprukhnytskyi.macrotrackerbffservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AdaptiveCalorieServiceTest {
    private AdaptiveCalorieService service;
    private AtomicReference<String> evaluationPath;
    private AtomicReference<HttpMethod> evaluationMethod;

    @BeforeEach
    void setUp() {
        evaluationPath = new AtomicReference<>();
        evaluationMethod = new AtomicReference<>();
        WebClient userClient = WebClient.builder()
                .exchangeFunction(request -> {
                    if ("/api/users/me/entitlements".equals(request.url().getPath())) {
                        return json("{\"features\":{\"adaptiveCalories\":true}}");
                    }
                    evaluationPath.set(request.url().getPath());
                    evaluationMethod.set(request.method());
                    return json("""
                            {
                              "eligible": true,
                              "currentCalories": 2100,
                              "suggestedCalories": 2050,
                              "calorieDelta": -50,
                              "status": "ADJUSTMENT_RECOMMENDED"
                            }
                            """);
                })
                .build();
        WebClient intakeClient = clientReturning("""
                [{"date":"%s","calories":2050}]
                """.formatted(LocalDate.now()));
        WebClient weightClient = clientReturning("""
                [{"date":"%s","weight":80.0}]
                """.formatted(LocalDate.now()));
        service = new AdaptiveCalorieService(userClient, intakeClient, weightClient);
    }

    @Test
    void recommendation_shouldDelegateCalculationToUserService() {
        StepVerifier.create(service.recommendation(42L))
                .assertNext(value -> {
                    assertThat(value.getCalorieDelta()).isEqualTo(-50);
                    assertThat(value.getStatus()).isEqualTo("ADJUSTMENT_RECOMMENDED");
                })
                .verifyComplete();

        assertThat(evaluationPath.get())
                .isEqualTo("/internal/profile/adaptive-calories");
        assertThat(evaluationMethod.get()).isEqualTo(HttpMethod.POST);
    }

    private static WebClient clientReturning(String body) {
        return WebClient.builder()
                .exchangeFunction(request -> json(body))
                .build();
    }

    private static Mono<ClientResponse> json(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .build());
    }
}
