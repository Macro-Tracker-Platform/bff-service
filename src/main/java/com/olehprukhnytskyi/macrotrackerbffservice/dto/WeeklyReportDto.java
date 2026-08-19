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
public class WeeklyReportDto {
    private LocalDate from;
    private LocalDate to;
    private int recordedDays;
    private int periodDays;
    private BigDecimal averageCalories;
    private BigDecimal averageProtein;
    private int daysWithinCalorieGoal;
    private BigDecimal averageWeightChange;
    private boolean sufficientData;
    private List<Recommendation> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String code;
        private String message;
    }
}
