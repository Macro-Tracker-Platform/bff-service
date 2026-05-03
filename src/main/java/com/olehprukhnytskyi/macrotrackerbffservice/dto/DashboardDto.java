package com.olehprukhnytskyi.macrotrackerbffservice.dto;

import com.olehprukhnytskyi.dto.PagedResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "User nutrition goals and paginated intake history")
public class DashboardDto {
    @Schema(description = "User daily nutrition targets")
    private UserGoalDto goal;

    @Schema(description = "Paginated list of food intake records")
    private List<IntakeDto> intakes;

    @Schema(description = "User profile")
    private UserDetailsDto profile;

    @Schema(description = "User weight logs")
    private PagedResponse<WeightLogDto> weightLogs;
}
