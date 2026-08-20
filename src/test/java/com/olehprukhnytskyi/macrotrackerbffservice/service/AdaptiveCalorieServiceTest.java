package com.olehprukhnytskyi.macrotrackerbffservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackerbffservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.DailyNutritionSummaryDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.GoalChangeDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserDetailsDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.UserGoalDto;
import com.olehprukhnytskyi.macrotrackerbffservice.dto.WeightLogDto;
import com.olehprukhnytskyi.util.Goal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveCalorieServiceTest {
    private final AdaptiveCalorieService service = new AdaptiveCalorieService(null, null, null);

    @Test
    void weeklyCheckInExplainsAdjustmentAndForecast() {
        LocalDate today = LocalDate.now();
        List<DailyNutritionSummaryDto> summaries = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            DailyNutritionSummaryDto row = new DailyNutritionSummaryDto();
            row.setDate(today.minusDays(index));
            row.setCalories(BigDecimal.valueOf(2000));
            summaries.add(row);
        }
        List<WeightLogDto> weights = List.of(
                weight(today.minusDays(18), "80.0"),
                weight(today.minusDays(12), "79.9"),
                weight(today.minusDays(6), "79.8"),
                weight(today, "79.7"));
        UserGoalDto goal = new UserGoalDto();
        goal.setCalories(2000);
        UserDetailsDto profile = UserDetailsDto.builder().goal(Goal.LOSE)
                .weight(80).goalWeight(70).build();

        AdaptiveCalorieRecommendationDto result = service.build(
                summaries, weights, goal, profile, new GoalChangeDto());

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getStatus()).isEqualTo("ADJUSTMENT_RECOMMENDED");
        assertThat(result.getSuggestedCalories()).isEqualTo(1900);
        assertThat(result.getEstimatedMaintenanceCalories()).isGreaterThan(2000);
        assertThat(result.getEstimatedGoalDate()).isAfter(today);
        assertThat(result.getExplanation()).contains("2000 → 1900");
    }

    private WeightLogDto weight(LocalDate date, String value) {
        return WeightLogDto.builder().date(date).weight(new BigDecimal(value)).build();
    }
}
