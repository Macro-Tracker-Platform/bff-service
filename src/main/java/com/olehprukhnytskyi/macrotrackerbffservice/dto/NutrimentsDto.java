package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.olehprukhnytskyi.macrotrackerbffservice.util.BigDecimalJsonSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Schema(description = "Nutritional values for food intake")
public class NutrimentsDto {
    @Schema(
            description = "Total calories",
            example = "165.5",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal calories = BigDecimal.ZERO;

    @Schema(
            description = "Total carbohydrates",
            example = "12.0",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal carbohydrates = BigDecimal.ZERO;

    @Schema(
            description = "Total fat",
            example = "3.2",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal fat = BigDecimal.ZERO;

    @Schema(
            description = "Total protein",
            example = "31.0",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal protein = BigDecimal.ZERO;

    @Schema(
            description = "Calories per piece",
            example = "165.5",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal caloriesPerPiece = BigDecimal.ZERO;

    @Schema(
            description = "Carbohydrates per piece",
            example = "12.0",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal carbohydratesPerPiece = BigDecimal.ZERO;

    @Schema(
            description = "Fat per piece",
            example = "3.2",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal fatPerPiece = BigDecimal.ZERO;

    @Schema(
            description = "Protein per piece",
            example = "31.0",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal proteinPerPiece = BigDecimal.ZERO;

    @Schema(
            description = "Calories per 100g",
            example = "165.5",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal caloriesPer100 = BigDecimal.ZERO;

    @Schema(
            description = "Carbohydrates per 100g",
            example = "12.0",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal carbohydratesPer100 = BigDecimal.ZERO;

    @Schema(
            description = "Fat per 100g",
            example = "3.2",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal fatPer100 = BigDecimal.ZERO;

    @Schema(
            description = "Protein per 100g",
            example = "31.0",
            type = "number",
            format = "decimal"
    )
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal proteinPer100 = BigDecimal.ZERO;
}
