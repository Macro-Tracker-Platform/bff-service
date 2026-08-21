package com.olehprukhnytskyi.macrotrackerbffservice.controller;

import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.InsightsDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeeklyReportDto;
import com.olehprukhnytskyi.macrotrackerbffservice.service.AdaptiveCalorieService;
import com.olehprukhnytskyi.macrotrackerbffservice.service.InsightsService;
import com.olehprukhnytskyi.util.CustomHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(InsightsController.class)
class InsightsControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private InsightsService insightsService;

    @MockitoBean
    private AdaptiveCalorieService adaptiveCalorieService;

    @Test
    void insightsShouldNotBeCached() {
        when(insightsService.getInsights(1L, "30d", null))
                .thenReturn(Mono.just(InsightsDto.builder().period("30d").build()));

        webTestClient.get()
                .uri("/api/insights?period=30d")
                .header(CustomHeaders.X_USER_ID, "1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Test
    void weeklyReportShouldNotBeCached() {
        when(insightsService.getWeeklyReport(1L, null, null))
                .thenReturn(Mono.just(WeeklyReportDto.builder().build()));

        webTestClient.get()
                .uri("/api/insights/weekly-report")
                .header(CustomHeaders.X_USER_ID, "1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Test
    void adaptiveCaloriesShouldNotBeCached() {
        when(adaptiveCalorieService.recommendation(1L))
                .thenReturn(Mono.just(AdaptiveCalorieRecommendationDto.builder().build()));

        webTestClient.get()
                .uri("/api/insights/adaptive-calories")
                .header(CustomHeaders.X_USER_ID, "1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
