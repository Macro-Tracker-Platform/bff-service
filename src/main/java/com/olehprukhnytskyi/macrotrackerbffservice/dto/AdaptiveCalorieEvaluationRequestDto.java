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
public class AdaptiveCalorieEvaluationRequestDto {
    private List<DailyCalorieSampleDto> summaries;
    private List<WeightSampleDto> weights;

    public static AdaptiveCalorieEvaluationRequestDto from(
            List<DailyNutritionSummaryDto> summaries, List<WeightLogDto> weights) {
        return AdaptiveCalorieEvaluationRequestDto.builder()
                .summaries(summaries.stream()
                        .map(item -> new DailyCalorieSampleDto(
                                item.getDate(), item.getCalories()))
                        .toList())
                .weights(weights.stream()
                        .map(item -> new WeightSampleDto(item.getDate(), item.getWeight()))
                        .toList())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCalorieSampleDto {
        private LocalDate date;
        private BigDecimal calories;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightSampleDto {
        private LocalDate date;
        private BigDecimal weight;
    }
}
