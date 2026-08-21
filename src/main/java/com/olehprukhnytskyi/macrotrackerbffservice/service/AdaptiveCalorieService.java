package com.olehprukhnytskyi.macrotrackerbffservice.service;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.AdaptiveCalorieEvaluationRequestDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.DailyNutritionSummaryDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserEntitlementDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeightLogDto;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdaptiveCalorieService {
    private static final int ANALYSIS_DAYS = 21;
    private final WebClient userWebClient;
    private final WebClient intakeWebClient;
    private final WebClient weightWebClient;

    public Mono<AdaptiveCalorieRecommendationDto> recommendation(Long userId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(ANALYSIS_DAYS - 1L);
        Mono<List<DailyNutritionSummaryDto>> summaries = intakeWebClient.get()
                .uri(builder -> builder.path("/internal/intakes/daily-summary")
                        .queryParam("from", from).queryParam("to", to).build())
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
        Mono<List<WeightLogDto>> weights = weightWebClient.get()
                .uri(builder -> builder.path("/api/weights/range")
                        .queryParam("startDate", from).queryParam("endDate", to).build())
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
        Mono<Void> entitlement = userWebClient.get()
                .uri("/api/users/me/entitlements")
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(UserEntitlementDto.class)
                .flatMap(value -> value.getFeatures() != null
                        && value.getFeatures().isAdaptiveCalories() ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Adaptive calorie guidance requires MacroTracker Pro")));

        return entitlement.then(Mono.zip(summaries, weights))
                .flatMap(tuple -> userWebClient.post()
                        .uri("/internal/profile/adaptive-calories")
                        .header(CustomHeaders.X_USER_ID, userId.toString())
                        .bodyValue(AdaptiveCalorieEvaluationRequestDto.from(
                                tuple.getT1(), tuple.getT2()))
                        .retrieve()
                        .bodyToMono(AdaptiveCalorieRecommendationDto.class));
    }
}
