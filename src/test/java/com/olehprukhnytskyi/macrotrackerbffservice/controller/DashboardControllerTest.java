package com.olehprukhnytskyi.macrotrackerbffservice.controller;

import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.DashboardDto;
import com.olehprukhnytskyi.macrotrackerbffservice.service.DashboardService;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(DashboardController.class)
@Import(DashboardService.class)
class DashboardControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    @DisplayName("Should return dashboard")
    void getDashboard_shouldReturnDashboard() {
        // Given
        DashboardDto dto = DashboardDto.builder().build();

        LocalDate now = LocalDate.now();

        when(dashboardService.getDashboard(1L, now, null)).thenReturn(Mono.just(dto));

        // When & Then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/dashboard")
                        .queryParam("date", now)
                        .build())
                .header(CustomHeaders.X_USER_ID, "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardDto.class)
                .isEqualTo(dto);
    }
}
