package com.olehprukhnytskyi.macrotrackerbffservice.service;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DashboardServiceTest {
    private DashboardService dashboardService;

    @BeforeEach
    void setup() {
        WebClient mockClient = WebClient.builder()
                .exchangeFunction(request -> {
                    String path = request.url().getPath();
                    if (path.contains("/goal")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body("{\"goal\":123}")
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .build());
                    } else if (path.contains("/intake")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body("[{\"id\": 1, \"calories\": 500}]")
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .build());
                    } else if (path.contains("/details")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body("{\"profile\":123}")
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .build());
                    } else if (path.contains("/weights")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body("[{\"id\": 1, \"weights\": 500}]")
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
                })
                .build();
        dashboardService = new DashboardService(mockClient, mockClient, mockClient);
    }

    @Test
    @DisplayName("Should aggregate dashboard data")
    void getDashboard_shouldAggregateDashboardData() {
        StepVerifier.create(dashboardService.getDashboard(1L, LocalDate.now()))
                .expectNextMatches(dto -> dto.getGoal() != null)
                .verifyComplete();
    }
}
