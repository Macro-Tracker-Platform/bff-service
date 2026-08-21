package com.olehprukhnytskyi.macrotrackerbffservice.controller;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.InsightsDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeeklyReportDto;
import com.olehprukhnytskyi.macrotrackerbffservice.service.AdaptiveCalorieService;
import com.olehprukhnytskyi.macrotrackerbffservice.service.InsightsService;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/insights")
public class InsightsController {
    private static final String APP_VERSION_CODE_HEADER = "X-App-Version-Code";

    private final InsightsService insightsService;
    private final AdaptiveCalorieService adaptiveCalorieService;

    @GetMapping
    public Mono<ResponseEntity<InsightsDto>> insights(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode,
            @RequestParam(defaultValue = "30d") String period) {
        return insightsService.getInsights(userId, period, appVersionCode)
                .map(this::noStore);
    }

    @GetMapping("/weekly-report")
    public Mono<ResponseEntity<WeeklyReportDto>> weeklyReport(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return insightsService.getWeeklyReport(userId, weekStart, appVersionCode)
                .map(this::noStore);
    }

    @GetMapping("/adaptive-calories")
    public Mono<ResponseEntity<AdaptiveCalorieRecommendationDto>> adaptiveCalories(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return adaptiveCalorieService.recommendation(userId).map(this::noStore);
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
