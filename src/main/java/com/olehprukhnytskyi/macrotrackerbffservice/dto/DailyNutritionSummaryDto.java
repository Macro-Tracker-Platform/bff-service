package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class DailyNutritionSummaryDto {
    private LocalDate date;
    private BigDecimal calories;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carbohydrates;
}
