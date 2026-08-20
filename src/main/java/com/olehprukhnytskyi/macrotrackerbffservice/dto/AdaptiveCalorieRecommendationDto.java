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
public class AdaptiveCalorieRecommendationDto {
    private boolean eligible;
    private int loggedDays;
    private int requiredLoggedDays;
    private int weightEntries;
    private int requiredWeightEntries;
    private int weightSpanDays;
    private Integer currentCalories;
    private Integer suggestedCalories;
    private Integer calorieDelta;
    private BigDecimal observedKgPerWeek;
    private BigDecimal targetKgPerWeek;
    private Integer estimatedMaintenanceCalories;
    private Integer estimatedWeeksToGoal;
    private LocalDate estimatedGoalDate;
    private LocalDate nextCheckInDate;
    private String status;
    private String explanation;
    private List<String> blockers;
}
