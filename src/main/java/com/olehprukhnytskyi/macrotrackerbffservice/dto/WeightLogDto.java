package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object containing a single weight record")
public class WeightLogDto {
    @Schema(description = "Unique identifier of the weight log", example = "105")
    private Long id;

    @Schema(description = "Recorded weight in kilograms", example = "75.5")
    private BigDecimal weight;

    @Schema(description = "Date of the weight record", example = "2023-10-28")
    private LocalDate date;
}
