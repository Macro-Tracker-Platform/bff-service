package com.olehprukhnytskyi.macrotrackerbffservice.controller;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.DashboardDto;
import com.olehprukhnytskyi.macrotrackerbffservice.service.DashboardService;
import com.olehprukhnytskyi.util.CustomHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
@Tag(
        name = "Dashboard API",
        description = "API for aggregating and retrieving user nutrition dashboard data"
)
public class DashboardController {
    private static final String APP_VERSION_CODE_HEADER = "X-App-Version-Code";
    private final DashboardService dashboardService;

    @Operation(
            summary = "Aggregate dashboard data",
            description = "Combines user goals (calories, proteins, fats, carbs) "
                    + "with list of daily nutrition intakes")
    @GetMapping
    public Mono<ResponseEntity<DashboardDto>> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode) {
        log.debug("Received dashboard request for userId={}", userId);
        return dashboardService.getDashboard(userId, date, appVersionCode)
                .doOnSuccess(dto -> log
                        .debug("Dashboard data aggregated successfully for userId={}", userId))
                .doOnError(error -> log
                        .error("Failed to aggregate dashboard data for userId={}", userId, error))
                .map(ResponseEntity::ok);
    }
}
