package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User profile details")
public class UserDetailsDto {
    @Schema(description = "User age in years", example = "25")
    private Integer age;

    @Schema(description = "User weight in kilograms", example = "70")
    private Integer weight;

    @Schema(description = "User goal weight in kilograms", example = "70")
    private Integer goalWeight;

    @Schema(description = "User height in centimeters", example = "175")
    private Integer height;

    @Schema(description = "User gender")
    private Gender gender;

    @Schema(description = "User activity level")
    private ActivityLevel activityLevel;

    @Schema(description = "User fitness goal")
    private Goal goal;

    @Schema(description = "User body type")
    private BodyType bodyType;
}
