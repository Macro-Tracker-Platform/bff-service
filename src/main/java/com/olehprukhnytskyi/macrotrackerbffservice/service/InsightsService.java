package com.olehprukhnytskyi.macrotrackerbffservice.service;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.DailyNutritionSummaryDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.InsightsDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserEntitlementDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserGoalDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeeklyReportDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeightLogDto;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class InsightsService {
    private static final String APP_VERSION_CODE_HEADER = "X-App-Version-Code";
    private static final BigDecimal CALORIE_LOWER = new BigDecimal("0.90");
    private static final BigDecimal CALORIE_UPPER = new BigDecimal("1.10");
    private static final BigDecimal CALORIE_HIGH = new BigDecimal("1.15");
    private static final BigDecimal PROTEIN_TARGET = new BigDecimal("0.90");

    private final WebClient userWebClient;
    private final WebClient intakeWebClient;
    private final WebClient weightWebClient;

    public Mono<InsightsDto> getInsights(Long userId, String period, String appVersionCode) {
        int days = parsePeriod(period);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        LocalDate fetchFrom = from.isBefore(to.minusDays(13)) ? from : to.minusDays(13);
        return ensureAdvancedInsights(userId, appVersionCode)
                .then(fetch(userId, fetchFrom, to))
                .map(data -> buildInsights(period, from, to, data));
    }

    public Mono<WeeklyReportDto> getWeeklyReport(Long userId, LocalDate requestedStart,
                                                 String appVersionCode) {
        LocalDate start = requestedStart == null
                ? LocalDate.now().minusDays(6)
                : requestedStart;
        LocalDate end = start.plusDays(6);
        return ensureAdvancedInsights(userId, appVersionCode)
                .then(fetch(userId, start, end))
                .map(data -> buildWeeklyReport(start, end, data));
    }

    private Mono<Void> ensureAdvancedInsights(Long userId, String appVersionCode) {
        return userWebClient.get()
                .uri("/api/users/me/entitlements")
                .headers(headers -> {
                    headers.set(CustomHeaders.X_USER_ID, userId.toString());
                    if (appVersionCode != null && !appVersionCode.isBlank()) {
                        headers.set(APP_VERSION_CODE_HEADER, appVersionCode);
                    }
                })
                .retrieve()
                .bodyToMono(UserEntitlementDto.class)
                .flatMap(entitlement -> entitlement.getFeatures() != null
                        && entitlement.getFeatures().isAdvancedInsights()
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "MacroTracker Pro is required for advanced insights")));
    }

    private Mono<InsightData> fetch(Long userId, LocalDate from, LocalDate to) {
        Mono<List<DailyNutritionSummaryDto>> summaries = intakeWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/intakes/daily-summary")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
        Mono<UserGoalDto> goals = userWebClient.get()
                .uri("/api/profile/goal")
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(UserGoalDto.class);
        Mono<List<WeightLogDto>> weights = weightWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/weights/range")
                        .queryParam("startDate", from)
                        .queryParam("endDate", to)
                        .build())
                .header(CustomHeaders.X_USER_ID, userId.toString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
        return Mono.zip(summaries, goals, weights)
                .map(tuple -> new InsightData(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private InsightsDto buildInsights(String period, LocalDate from, LocalDate to,
                                      InsightData data) {
        List<DailyNutritionSummaryDto> periodRows = data.summaries().stream()
                .filter(row -> !row.getDate().isBefore(from))
                .toList();
        List<WeightLogDto> periodWeights = data.weights().stream()
                .filter(row -> !row.getDate().isBefore(from))
                .sorted(Comparator.comparing(WeightLogDto::getDate))
                .toList();
        Map<LocalDate, DailyNutritionSummaryDto> summariesByDate = new HashMap<>();
        periodRows.forEach(row -> summariesByDate.put(row.getDate(), row));
        Map<LocalDate, BigDecimal> weightsByDate = new HashMap<>();
        periodWeights.forEach(row -> weightsByDate.put(row.getDate(), row.getWeight()));
        List<InsightsDto.ChartPoint> chart = from.datesUntil(to.plusDays(1))
                .map(date -> InsightsDto.ChartPoint.builder()
                        .date(date)
                        .calories(summariesByDate.containsKey(date)
                                ? summariesByDate.get(date).getCalories() : null)
                        .weight(weightsByDate.get(date))
                        .build())
                .toList();
        return InsightsDto.builder()
                .period(period)
                .from(from)
                .to(to)
                .recordedDays(periodRows.size())
                .averageCalories(average(periodRows, Metric.CALORIES))
                .averageProtein(average(periodRows, Metric.PROTEIN))
                .averageFat(average(periodRows, Metric.FAT))
                .averageCarbohydrates(average(periodRows, Metric.CARBOHYDRATES))
                .daysWithinCalorieGoal(countCaloriesInRange(periodRows, data.goals()))
                .daysMeetingProteinGoal(countProteinGoal(periodRows, data.goals()))
                .weekComparison(buildWeekComparison(to, data.summaries()))
                .weightTrend(buildWeightTrend(periodWeights))
                .chart(chart)
                .build();
    }

    private WeeklyReportDto buildWeeklyReport(LocalDate from, LocalDate to,
                                              InsightData data) {
        List<DailyNutritionSummaryDto> rows = data.summaries();
        List<WeeklyReportDto.Recommendation> recommendations = new ArrayList<>();
        if (rows.size() < 4) {
            recommendations.add(recommendation("INSUFFICIENT_DATA",
                    "Not enough logged days to draw a reliable conclusion."));
        } else {
            if (countProteinGoal(rows, data.goals()) * 2 < rows.size()) {
                recommendations.add(recommendation("PROTEIN_BELOW_TARGET",
                        "Protein reached at least 90% of the goal on fewer "
                                + "than half of logged days."));
            }
            if (countHighCalories(rows, data.goals()) >= 2) {
                recommendations.add(recommendation("CALORIES_ABOVE_TARGET",
                        "Calories were more than 15% above the goal on multiple logged days."));
            }
        }
        List<WeightLogDto> weights = data.weights().stream()
                .sorted(Comparator.comparing(WeightLogDto::getDate))
                .toList();
        if (weights.size() < 3) {
            recommendations.add(recommendation("INSUFFICIENT_WEIGHT_DATA",
                    "At least three weight measurements are needed for a weight trend."));
        }
        return WeeklyReportDto.builder()
                .from(from)
                .to(to)
                .recordedDays(rows.size())
                .periodDays(7)
                .averageCalories(average(rows, Metric.CALORIES))
                .averageProtein(average(rows, Metric.PROTEIN))
                .daysWithinCalorieGoal(countCaloriesInRange(rows, data.goals()))
                .averageWeightChange(weights.size() < 3 ? null
                        : weights.get(weights.size() - 1).getWeight()
                        .subtract(weights.get(0).getWeight()))
                .sufficientData(rows.size() >= 4 && weights.size() >= 3)
                .recommendations(recommendations)
                .build();
    }

    private InsightsDto.WeekComparison buildWeekComparison(
            LocalDate today, List<DailyNutritionSummaryDto> rows) {
        LocalDate currentFrom = today.minusDays(6);
        LocalDate previousFrom = currentFrom.minusDays(7);
        List<DailyNutritionSummaryDto> current = between(rows, currentFrom, today);
        List<DailyNutritionSummaryDto> previous = between(
                rows, previousFrom, currentFrom.minusDays(1));
        BigDecimal currentAverage = average(current, Metric.CALORIES);
        BigDecimal previousAverage = average(previous, Metric.CALORIES);
        return InsightsDto.WeekComparison.builder()
                .currentRecordedDays(current.size())
                .previousRecordedDays(previous.size())
                .currentAverageCalories(currentAverage)
                .previousAverageCalories(previousAverage)
                .calorieDifference(currentAverage == null || previousAverage == null
                        ? null : currentAverage.subtract(previousAverage))
                .build();
    }

    private InsightsDto.WeightTrend buildWeightTrend(List<WeightLogDto> weights) {
        if (weights.size() < 3) {
            return InsightsDto.WeightTrend.builder()
                    .measurementCount(weights.size())
                    .build();
        }
        BigDecimal start = weights.get(0).getWeight();
        BigDecimal end = weights.get(weights.size() - 1).getWeight();
        return InsightsDto.WeightTrend.builder()
                .measurementCount(weights.size())
                .startWeight(start)
                .endWeight(end)
                .change(end.subtract(start))
                .build();
    }

    private int countCaloriesInRange(List<DailyNutritionSummaryDto> rows, UserGoalDto goal) {
        BigDecimal target = BigDecimal.valueOf(goal.getCalories());
        BigDecimal lower = target.multiply(CALORIE_LOWER);
        BigDecimal upper = target.multiply(CALORIE_UPPER);
        return (int) rows.stream().filter(row -> row.getCalories().compareTo(lower) >= 0
                && row.getCalories().compareTo(upper) <= 0).count();
    }

    private int countHighCalories(List<DailyNutritionSummaryDto> rows, UserGoalDto goal) {
        BigDecimal high = BigDecimal.valueOf(goal.getCalories()).multiply(CALORIE_HIGH);
        return (int) rows.stream().filter(row -> row.getCalories().compareTo(high) > 0).count();
    }

    private int countProteinGoal(List<DailyNutritionSummaryDto> rows, UserGoalDto goal) {
        BigDecimal target = BigDecimal.valueOf(goal.getProtein()).multiply(PROTEIN_TARGET);
        return (int) rows.stream().filter(row -> row.getProtein().compareTo(target) >= 0).count();
    }

    private BigDecimal average(List<DailyNutritionSummaryDto> rows, Metric metric) {
        if (rows.isEmpty()) {
            return null;
        }
        BigDecimal total = rows.stream().map(metric::read)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
    }

    private List<DailyNutritionSummaryDto> between(List<DailyNutritionSummaryDto> rows,
                                                   LocalDate from, LocalDate to) {
        return rows.stream().filter(row -> !row.getDate().isBefore(from)
                && !row.getDate().isAfter(to)).toList();
    }

    private int parsePeriod(String period) {
        return switch (period) {
            case "7d" -> 7;
            case "30d" -> 30;
            case "90d" -> 90;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "period must be 7d, 30d or 90d");
        };
    }

    private WeeklyReportDto.Recommendation recommendation(String code, String message) {
        return WeeklyReportDto.Recommendation.builder().code(code).message(message).build();
    }

    private enum Metric {
        CALORIES {
            BigDecimal read(DailyNutritionSummaryDto value) {
                return value.getCalories();
            }
        },
        PROTEIN {
            BigDecimal read(DailyNutritionSummaryDto value) {
                return value.getProtein();
            }
        },
        FAT {
            BigDecimal read(DailyNutritionSummaryDto value) {
                return value.getFat();
            }
        },
        CARBOHYDRATES {
            BigDecimal read(DailyNutritionSummaryDto value) {
                return value.getCarbohydrates();
            }
        };

        abstract BigDecimal read(DailyNutritionSummaryDto value);
    }

    private record InsightData(List<DailyNutritionSummaryDto> summaries,
                               UserGoalDto goals, List<WeightLogDto> weights) {
    }
}
