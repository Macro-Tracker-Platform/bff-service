package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.ModerationStatus;
import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Food intake record")
public class IntakeDto {
    public enum Status {
        CONSUMED,
        PLANNED
    }

    @Schema(description = "Intake record ID", example = "1")
    private Long id;

    @Schema(
            description = "ID grouping multiple foods consumed in one meal (e.g., from a template)",
            example = "987fc3-a1b2-44"
    )
    private String mealGroupId;

    @Schema(
            description = "Food product ID",
            example = "507f1f77bcf86cd799439011"
    )
    private String foodId;

    @Schema(description = "Name of consumed food", example = "Chicken Breast")
    private String foodName;

    @Schema(description = "Amount consumed in grams", example = "200")
    private int amount;

    @Schema(description = "Available measurement units for the product", example = "GRAMS")
    private UnitType unitType;

    @Schema(description = "Date of consumption", example = "2024-01-15")
    private LocalDate date;

    @Schema(description = "Consumption period", example = "BREAKFAST")
    private IntakePeriod intakePeriod;

    private Status status;

    @Schema(description = "Nutritional values")
    private NutrimentsDto nutriments = new NutrimentsDto();

    private List<UnitType> availableUnits;

    private String originalFoodId;

    private ModerationStatus moderationStatus = ModerationStatus.NONE;

    private boolean verifiedByAdmin = false;
}
