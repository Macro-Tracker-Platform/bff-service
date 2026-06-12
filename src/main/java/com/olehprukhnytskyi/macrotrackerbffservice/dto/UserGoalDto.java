package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "User daily nutrition goals")
public class UserGoalDto {
    @Schema(description = "Daily calorie target", example = "2000")
    private int calories;

    @Schema(description = "Daily protein target in grams", example = "150")
    private int protein;

    @Schema(description = "Daily fat target in grams", example = "65")
    private int fat;

    @Schema(description = "Daily carbohydrates target in grams", example = "250")
    private int carbohydrates;

    @Schema(description = "Daily water target in milliliters", example = "2500")
    private int waterGoalMl;

    @Schema(description = "Whether the water goal is calculated or customized")
    private String waterGoalMode;
}
