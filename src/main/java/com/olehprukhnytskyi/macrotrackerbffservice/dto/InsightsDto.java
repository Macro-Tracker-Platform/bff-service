package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightsDto {
    private String period;
    private LocalDate from;
    private LocalDate to;
    private int recordedDays;
    private BigDecimal averageCalories;
    private BigDecimal averageProtein;
    private BigDecimal averageFat;
    private BigDecimal averageCarbohydrates;
    private int daysWithinCalorieGoal;
    private int daysMeetingProteinGoal;
    private WeekComparison weekComparison;
    private WeightTrend weightTrend;
    private List<ChartPoint> chart;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekComparison {
        private int currentRecordedDays;
        private int previousRecordedDays;
        private BigDecimal currentAverageCalories;
        private BigDecimal previousAverageCalories;
        private BigDecimal calorieDifference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightTrend {
        private int measurementCount;
        private BigDecimal startWeight;
        private BigDecimal endWeight;
        private BigDecimal change;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartPoint {
        private LocalDate date;
        private BigDecimal calories;
        private Integer calorieGoal;
        private BigDecimal nutritionPercent;
        private BigDecimal weight;
        private Integer waterMl;
        private Integer waterGoalMl;
        private BigDecimal bmi;
        private BigDecimal goalBmi;
    }
}
