package com.olehprukhnytskyi.macrotrackerbffservice.service;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.DailyNutritionSummaryDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.GoalChangeDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserDetailsDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserEntitlementDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserGoalDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeightLogDto;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int REQUIRED_LOGGED_DAYS = 10;
    private static final int REQUIRED_WEIGHTS = 4;
    private static final int REQUIRED_WEIGHT_SPAN = 10;
    private static final int ADJUSTMENT = 100;
    private final WebClient userWebClient;
    private final WebClient intakeWebClient;
    private final WebClient weightWebClient;

    public Mono<AdaptiveCalorieRecommendationDto> recommendation(Long userId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(20);
        Mono<List<DailyNutritionSummaryDto>> summaries = intakeWebClient.get()
                .uri(builder -> builder.path("/internal/intakes/daily-summary")
                        .queryParam("from", from).queryParam("to", to).build())
                .header(CustomHeaders.X_USER_ID, userId.toString()).retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
        Mono<List<WeightLogDto>> weights = weightWebClient.get()
                .uri(builder -> builder.path("/api/weights/range")
                        .queryParam("startDate", from).queryParam("endDate", to).build())
                .header(CustomHeaders.X_USER_ID, userId.toString()).retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
        Mono<UserGoalDto> goal = userWebClient.get().uri("/api/profile/goal")
                .header(CustomHeaders.X_USER_ID, userId.toString()).retrieve()
                .bodyToMono(UserGoalDto.class);
        Mono<UserDetailsDto> profile = userWebClient.get().uri("/api/profile/details")
                .header(CustomHeaders.X_USER_ID, userId.toString()).retrieve()
                .bodyToMono(UserDetailsDto.class);
        Mono<GoalChangeDto> lastChange = userWebClient.get()
                .uri("/api/profile/goal/last-change")
                .header(CustomHeaders.X_USER_ID, userId.toString()).retrieve()
                .bodyToMono(GoalChangeDto.class);
        Mono<Void> entitlement = userWebClient.get().uri("/api/users/me/entitlements")
                .header(CustomHeaders.X_USER_ID, userId.toString()).retrieve()
                .bodyToMono(UserEntitlementDto.class)
                .flatMap(value -> value.getFeatures() != null
                        && value.getFeatures().isAdaptiveCalories() ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Adaptive calorie guidance requires MacroTracker Pro")));
        return entitlement.then(Mono.zip(summaries, weights, goal, profile, lastChange))
                .map(tuple -> build(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(),
                        tuple.getT5()));
    }

    private AdaptiveCalorieRecommendationDto build(List<DailyNutritionSummaryDto> summaries,
                                                    List<WeightLogDto> weights,
                                                    UserGoalDto goal,
                                                    UserDetailsDto profile,
                                                    GoalChangeDto goalChange) {
        List<WeightLogDto> ordered = weights.stream()
                .filter(item -> item.getDate() != null && item.getWeight() != null)
                .sorted(Comparator.comparing(WeightLogDto::getDate)).toList();
        int loggedDays = (int) summaries.stream()
                .filter(item -> item.getCalories() != null
                        && item.getCalories().compareTo(BigDecimal.ZERO) > 0).count();
        int span = ordered.size() < 2 ? 0 : (int) ChronoUnit.DAYS.between(
                ordered.getFirst().getDate(), ordered.getLast().getDate());
        List<String> blockers = new ArrayList<>();
        if (loggedDays < REQUIRED_LOGGED_DAYS) {
            blockers.add("Log food on " + (REQUIRED_LOGGED_DAYS - loggedDays) + " more days");
        }
        if (ordered.size() < REQUIRED_WEIGHTS) {
            blockers.add("Add " + (REQUIRED_WEIGHTS - ordered.size()) + " more weight entries");
        }
        if (span < REQUIRED_WEIGHT_SPAN) {
            blockers.add("Keep tracking weight for " + (REQUIRED_WEIGHT_SPAN - span)
                    + " more days");
        }
        if (goalChange.getLastChangedAt() != null
                && goalChange.getLastChangedAt().isAfter(LocalDate.now().minusDays(14))) {
            blockers.add("Keep the current goal for 14 days before adapting it again");
        }
        BigDecimal trend = regressionTrend(ordered);
        if (!blockers.isEmpty()) {
            return base(goal, loggedDays, ordered.size(), span, trend).eligible(false)
                    .explanation("More consistent data is needed before suggesting a safe change.")
                    .blockers(blockers).build();
        }
        int delta = chooseDelta(profile.getGoal() == null
                ? "MAINTAIN" : profile.getGoal().name(), trend);
        String explanation = delta == 0
                ? "Your recent weight trend is aligned with your goal. Keep your current target."
                : "Based on your logged intake and weight trend, a small "
                + (delta > 0 ? "increase" : "decrease") + " is the safest next step.";
        return base(goal, loggedDays, ordered.size(), span, trend).eligible(true)
                .suggestedCalories(goal.getCalories() + delta).calorieDelta(delta)
                .explanation(explanation).blockers(List.of()).build();
    }

    private AdaptiveCalorieRecommendationDto.AdaptiveCalorieRecommendationDtoBuilder base(
            UserGoalDto goal, int days, int weights, int span, BigDecimal trend) {
        return AdaptiveCalorieRecommendationDto.builder().loggedDays(days)
                .requiredLoggedDays(REQUIRED_LOGGED_DAYS).weightEntries(weights)
                .requiredWeightEntries(REQUIRED_WEIGHTS).weightSpanDays(span)
                .currentCalories(goal.getCalories()).observedKgPerWeek(trend);
    }

    private BigDecimal regressionTrend(List<WeightLogDto> rows) {
        if (rows.size() < 2) {
            return BigDecimal.ZERO;
        }
        LocalDate start = rows.getFirst().getDate();
        double meanX = rows.stream().mapToLong(r -> ChronoUnit.DAYS.between(start, r.getDate()))
                .average().orElse(0);
        double meanY = rows.stream().mapToDouble(r -> r.getWeight().doubleValue())
                .average().orElse(0);
        double numerator = 0;
        double denominator = 0;
        for (WeightLogDto row : rows) {
            double x = ChronoUnit.DAYS.between(start, row.getDate()) - meanX;
            numerator += x * (row.getWeight().doubleValue() - meanY);
            denominator += x * x;
        }
        double perWeek = denominator == 0 ? 0 : numerator / denominator * 7;
        return BigDecimal.valueOf(perWeek).setScale(2, RoundingMode.HALF_UP);
    }

    private int chooseDelta(String goal, BigDecimal trend) {
        double weekly = trend.doubleValue();
        if ("LOSE".equals(goal)) {
            if (weekly > -0.1) {
                return -ADJUSTMENT;
            }
            if (weekly < -1.0) {
                return ADJUSTMENT;
            }
        } else if ("GAIN".equals(goal)) {
            if (weekly < 0.1) {
                return ADJUSTMENT;
            }
            if (weekly > 0.75) {
                return -ADJUSTMENT;
            }
        } else if (weekly > 0.25) {
            return -ADJUSTMENT;
        } else if (weekly < -0.25) {
            return ADJUSTMENT;
        }
        return 0;
    }
}
