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
    private static final double MAX_PLAUSIBLE_WEEKLY_WEIGHT_CHANGE_KG = 1.5;
    private static final double MAX_ABSURD_WEEKLY_WEIGHT_CHANGE_KG = 4.0;
    private static final double MAX_PLAUSIBLE_ADJACENT_WEIGHT_CHANGE_KG = 3.0;
    private static final int MIN_PLAUSIBLE_MAINTENANCE_CALORIES = 1000;
    private static final int MAX_PLAUSIBLE_MAINTENANCE_CALORIES = 6000;
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

    AdaptiveCalorieRecommendationDto build(List<DailyNutritionSummaryDto> summaries,
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
        if (isImplausibleWeightData(ordered, trend)) {
            blockers.add("Recent weight changes are too volatile for a reliable estimate");
        }
        BigDecimal targetTrend = targetTrend(profile, ordered);
        Integer maintenance = estimatedMaintenance(summaries, trend);
        Integer weeksToGoal = estimatedWeeksToGoal(profile, ordered, targetTrend);
        LocalDate goalDate = weeksToGoal == null ? null
                : LocalDate.now().plusWeeks(weeksToGoal);
        LocalDate nextCheckIn = goalChange.getLastChangedAt() != null
                && goalChange.getLastChangedAt().isAfter(LocalDate.now().minusDays(14))
                ? goalChange.getLastChangedAt().plusDays(14) : LocalDate.now().plusWeeks(1);
        if (!blockers.isEmpty()) {
            return base(goal, loggedDays, ordered.size(), span, trend).eligible(false)
                    .targetKgPerWeek(targetTrend)
                    .estimatedMaintenanceCalories(maintenance)
                    .nextCheckInDate(nextCheckIn)
                    .status("BUILDING_DATA")
                    .explanation("More consistent data is needed before suggesting a safe change.")
                    .blockers(blockers).build();
        }
        int delta = chooseDelta(profile.getGoal() == null
                ? "MAINTAIN" : profile.getGoal().name(), trend, targetTrend);
        if (goal.getCalories() <= 1200 && delta < 0) {
            delta = 0;
        }
        int suggested = goal.getCalories() + delta;
        String explanation = explanation(goal.getCalories(), suggested, trend, targetTrend,
                delta);
        return base(goal, loggedDays, ordered.size(), span, trend).eligible(true)
                .suggestedCalories(suggested).calorieDelta(delta)
                .targetKgPerWeek(targetTrend)
                .estimatedMaintenanceCalories(maintenance)
                .estimatedWeeksToGoal(weeksToGoal)
                .estimatedGoalDate(goalDate)
                .nextCheckInDate(nextCheckIn)
                .status(delta == 0 ? "ON_TRACK" : "ADJUSTMENT_RECOMMENDED")
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

    private boolean isImplausibleWeightData(List<WeightLogDto> rows, BigDecimal trend) {
        if (trend.abs().compareTo(BigDecimal.valueOf(MAX_ABSURD_WEEKLY_WEIGHT_CHANGE_KG)) > 0) {
            return true;
        }
        for (int index = 1; index < rows.size(); index++) {
            BigDecimal previous = rows.get(index - 1).getWeight();
            BigDecimal current = rows.get(index).getWeight();
            if (current.subtract(previous).abs().compareTo(
                    BigDecimal.valueOf(MAX_PLAUSIBLE_ADJACENT_WEIGHT_CHANGE_KG)) > 0) {
                return true;
            }
        }
        return false;
    }

    private int chooseDelta(String goal, BigDecimal trend, BigDecimal targetTrend) {
        double weekly = trend.doubleValue();
        double target = targetTrend.doubleValue();
        if ("LOSE".equals(goal)) {
            if (weekly > target + 0.15) {
                return -ADJUSTMENT;
            }
            if (weekly < target - 0.25) {
                return ADJUSTMENT;
            }
        } else if ("GAIN".equals(goal)) {
            if (weekly < target - 0.10) {
                return ADJUSTMENT;
            }
            if (weekly > target + 0.20) {
                return -ADJUSTMENT;
            }
        } else if (weekly > 0.25) {
            return -ADJUSTMENT;
        } else if (weekly < -0.25) {
            return ADJUSTMENT;
        }
        return 0;
    }

    private BigDecimal targetTrend(UserDetailsDto profile, List<WeightLogDto> ordered) {
        double currentWeight = ordered.isEmpty() ? profile.getWeight() == null ? 70.0
                : profile.getWeight() : ordered.getLast().getWeight().doubleValue();
        String goal = profile.getGoal() == null ? "MAINTAIN" : profile.getGoal().name();
        double weekly = 0;
        if ("LOSE".equals(goal)) {
            weekly = -Math.max(0.25, Math.min(1.0, currentWeight * 0.005));
        } else if ("GAIN".equals(goal)) {
            weekly = Math.max(0.10, Math.min(0.50, currentWeight * 0.0025));
        }
        return BigDecimal.valueOf(weekly).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer estimatedMaintenance(List<DailyNutritionSummaryDto> summaries,
                                         BigDecimal trend) {
        if (trend.abs().compareTo(BigDecimal.valueOf(
                MAX_PLAUSIBLE_WEEKLY_WEIGHT_CHANGE_KG)) > 0) {
            return null;
        }
        List<BigDecimal> logged = summaries.stream().map(DailyNutritionSummaryDto::getCalories)
                .filter(value -> value != null
                        && value.compareTo(BigDecimal.valueOf(500)) >= 0
                        && value.compareTo(BigDecimal.valueOf(
                                MAX_PLAUSIBLE_MAINTENANCE_CALORIES)) <= 0)
                .toList();
        if (logged.isEmpty()) {
            return null;
        }
        BigDecimal average = logged.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logged.size()), 0, RoundingMode.HALF_UP);
        BigDecimal dailyStoredEnergy = trend.multiply(BigDecimal.valueOf(7700))
                .divide(BigDecimal.valueOf(7), 0, RoundingMode.HALF_UP);
        int estimate = average.subtract(dailyStoredEnergy).intValue();
        if (estimate < MIN_PLAUSIBLE_MAINTENANCE_CALORIES
                || estimate > MAX_PLAUSIBLE_MAINTENANCE_CALORIES) {
            return null;
        }
        return (int) Math.round(estimate / 10.0) * 10;
    }

    private Integer estimatedWeeksToGoal(UserDetailsDto profile, List<WeightLogDto> ordered,
                                         BigDecimal targetTrend) {
        if (profile.getGoalWeight() == null || targetTrend.signum() == 0) {
            return null;
        }
        double current = ordered.isEmpty() ? profile.getWeight() == null ? 0
                : profile.getWeight() : ordered.getLast().getWeight().doubleValue();
        if (current <= 0) {
            return null;
        }
        double distance = Math.abs(current - profile.getGoalWeight());
        return Math.max(1, (int) Math.ceil(distance / Math.abs(targetTrend.doubleValue())));
    }

    private String explanation(int current, int suggested, BigDecimal observed,
                               BigDecimal target, int delta) {
        String trendText = "Weight trend: " + observed + " kg/week; target: "
                + target + " kg/week. ";
        if (delta == 0) {
            return trendText + "You are on track, so keep your current " + current
                    + " kcal target.";
        }
        return trendText + "We recommend " + current + " → " + suggested
                + " kcal/day for the next week.";
    }
}
